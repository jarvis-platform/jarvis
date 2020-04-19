package com.xatkit.core.recognition.dialogflow;

import com.google.api.core.ApiFuture;
import com.google.api.gax.rpc.FailedPreconditionException;
import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.cloud.dialogflow.v2.Context;
import com.google.cloud.dialogflow.v2.ContextName;
import com.google.cloud.dialogflow.v2.DetectIntentResponse;
import com.google.cloud.dialogflow.v2.EntityType;
import com.google.cloud.dialogflow.v2.Intent;
import com.google.cloud.dialogflow.v2.ProjectAgentName;
import com.google.cloud.dialogflow.v2.ProjectName;
import com.google.cloud.dialogflow.v2.QueryInput;
import com.google.cloud.dialogflow.v2.QueryResult;
import com.google.cloud.dialogflow.v2.SessionName;
import com.google.cloud.dialogflow.v2.TextInput;
import com.google.cloud.dialogflow.v2.TrainAgentRequest;
import com.google.longrunning.Operation;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.xatkit.core.EventDefinitionRegistry;
import com.xatkit.core.XatkitException;
import com.xatkit.core.recognition.IntentRecognitionProvider;
import com.xatkit.core.recognition.RecognitionMonitor;
import com.xatkit.core.recognition.dialogflow.mapper.DialogFlowEntityMapper;
import com.xatkit.core.recognition.dialogflow.mapper.DialogFlowEntityReferenceMapper;
import com.xatkit.core.recognition.dialogflow.mapper.DialogFlowIntentMapper;
import com.xatkit.core.recognition.dialogflow.mapper.RecognizedIntentMapper;
import com.xatkit.core.session.XatkitSession;
import com.xatkit.intent.BaseEntityDefinition;
import com.xatkit.intent.CompositeEntityDefinition;
import com.xatkit.intent.CompositeEntityDefinitionEntry;
import com.xatkit.intent.CustomEntityDefinition;
import com.xatkit.intent.EntityDefinition;
import com.xatkit.intent.IntentDefinition;
import com.xatkit.intent.RecognizedIntent;
import fr.inria.atlanmod.commons.log.Log;
import lombok.NonNull;
import org.apache.commons.configuration2.Configuration;

import javax.annotation.Nullable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static fr.inria.atlanmod.commons.Preconditions.checkArgument;
import static fr.inria.atlanmod.commons.Preconditions.checkNotNull;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * A {@link IntentRecognitionProvider} implementation for the DialogFlow API.
 * <p>
 * This class is used to easily setup a connection to a given DialogFlow agent. The behavior of this connector can be
 * customized in the Xatkit {@link Configuration}, see {@link DialogFlowConfiguration} for more information on the
 * configuration options.
 */
public class DialogFlowApi extends IntentRecognitionProvider {

    /**
     * The {@link DialogFlowConfiguration} extracted from the provided {@link Configuration}.
     */
    private DialogFlowConfiguration configuration;

    /**
     * The clients used to access the DialogFlow API.
     */
    private DialogFlowClients dialogFlowClients;

    /**
     * Represents the DialogFlow project name.
     * <p>
     * This attribute is used to compute project-level operations, such as the training of the underlying
     * DialogFlow's agent.
     *
     * @see #trainMLEngine()
     */
    private ProjectName projectName;

    /**
     * Represents the DialogFlow agent name.
     * <p>
     * This attribute is used to compute intent-level operations, such as retrieving the list of registered
     * {@link Intent}s, or deleting specific {@link Intent}s.
     *
     * @see #registerIntentDefinition(IntentDefinition)
     * @see #deleteIntentDefinition(IntentDefinition)
     */
    private ProjectAgentName projectAgentName;

    /**
     * A local cache used to retrieve registered {@link Intent}s from their display name.
     * <p>
     * This cache is used to limit the number of calls to the DialogFlow API.
     */
    private Map<String, Intent> registeredIntents;

    /**
     * A local cache used to retrieve registered {@link EntityType}s from their display name.
     * <p>
     * This cache is used to limit the number of calls to the DialogFlow API.
     */
    private Map<String, EntityType> registeredEntityTypes;

    /**
     * The {@link RecognitionMonitor} used to track intent matching information.
     */
    @Nullable
    private RecognitionMonitor recognitionMonitor;

    /**
     * The mapper creating DialogFlow {@link Intent}s from {@link IntentDefinition} instances.
     */
    private DialogFlowIntentMapper dialogFlowIntentMapper;

    /**
     * The mapper creating DialogFlow {@link EntityType}s from {@link EntityDefinition} instances.
     */
    private DialogFlowEntityMapper dialogFlowEntityMapper;

    /**
     * The mapper creating DialogFlow entity references from {@link EntityDefinition} references.
     * <p>
     * These references are typically used to refer to {@link EntityType}s in {@link Intent}'s training sentences.
     */
    private DialogFlowEntityReferenceMapper dialogFlowEntityReferenceMapper;

    /**
     * The mapper creating {@link RecognizedIntent}s from {@link QueryResult} instances returned by DialogFlow.
     */
    private RecognizedIntentMapper recognizedIntentMapper;

    /**
     * Constructs a {@link DialogFlowApi} with the provided {@code eventRegistry}, {@code configuration}, and {@code
     * recognitionMonitor}.
     * <p>
     * The behavior of this class can be customized in the provided {@code configuration}. See
     * {@link DialogFlowConfiguration} for more information on the configuration options.
     *
     * @param eventRegistry      the {@link EventDefinitionRegistry} containing the events defined in the current bot
     * @param configuration      the {@link Configuration} holding the DialogFlow project ID and language code
     * @param recognitionMonitor the {@link RecognitionMonitor} instance storing intent matching information
     * @throws NullPointerException if the provided {@code eventRegistry}, {@code configuration} or one of the mandatory
     *                              {@code configuration} value is {@code null}.
     * @throws DialogFlowException  if the client failed to start a new session
     * @see DialogFlowConfiguration
     */
    public DialogFlowApi(@NonNull EventDefinitionRegistry eventRegistry, @NonNull Configuration configuration,
                         @Nullable RecognitionMonitor recognitionMonitor) {
        Log.info("Starting DialogFlow Client");
        this.configuration = new DialogFlowConfiguration(configuration);
        this.projectAgentName = ProjectAgentName.of(this.configuration.getProjectId());
        this.dialogFlowClients = new DialogFlowClients(this.configuration);
        this.projectName = ProjectName.of(this.configuration.getProjectId());
        this.dialogFlowEntityReferenceMapper = new DialogFlowEntityReferenceMapper();
        this.dialogFlowIntentMapper = new DialogFlowIntentMapper(this.configuration,
                this.dialogFlowEntityReferenceMapper);
        this.dialogFlowEntityMapper = new DialogFlowEntityMapper(this.dialogFlowEntityReferenceMapper);
        this.recognizedIntentMapper = new RecognizedIntentMapper(this.configuration, eventRegistry);
        this.cleanAgent();
        this.importRegisteredIntents();
        this.importRegisteredEntities();
        this.recognitionMonitor = recognitionMonitor;
    }

    /**
     * Deletes all the {@link Intent}s and {@link EntityType}s from the DialogFlow agent.
     * <p>
     * Agent cleaning is enabled by setting the property {@link DialogFlowConfiguration#CLEAN_AGENT_ON_STARTUP_KEY}
     * in the xatkit configuration file, and allows to easily re-deploy bots under development. Production-ready
     * agents should not be cleaned on startup: re-training the ML engine can take a while.
     */
    private void cleanAgent() {
        if (this.configuration.isCleanAgentOnStartup()) {
            Log.info("Cleaning agent DialogFlow agent");
            List<Intent> registeredIntents = getRegisteredIntents();
            for (Intent intent : registeredIntents) {
                if (!intent.getDisplayName().equals(DEFAULT_FALLBACK_INTENT.getName())) {
                    this.dialogFlowClients.getIntentsClient().deleteIntent(intent.getName());
                }
            }
            List<EntityType> registeredEntityTypes = getRegisteredEntityTypes();
            for (EntityType entityType : registeredEntityTypes) {
                this.dialogFlowClients.getEntityTypesClient().deleteEntityType(entityType.getName());
            }
        }
    }

    /**
     * Imports the intents registered in the DialogFlow project.
     * <p>
     * Intents import can be disabled to reduce the number of queries sent to the DialogFlow API by setting the
     * {@link DialogFlowConfiguration#ENABLE_INTENT_LOADING_KEY} property to {@code false} in the provided
     * {@link Configuration}. Note that disabling intents import may generate consistency issues when creating,
     * deleting, and matching intents.
     */
    private void importRegisteredIntents() {
        this.registeredIntents = new HashMap<>();
        if (this.configuration.isCleanAgentOnStartup()) {
            Log.info("Skipping intent import, the agent has been cleaned on startup");
            return;
        }
        if (configuration.isEnableIntentLoader()) {
            Log.info("Loading Intents previously registered in the DialogFlow project {0}", projectName
                    .getProject());
            for (Intent intent : getRegisteredIntents()) {
                registeredIntents.put(intent.getDisplayName(), intent);
            }
        } else {
            Log.info("Intent loading is disabled, existing Intents in the DialogFlow project {0} will not be " +
                    "imported", projectName.getProject());
        }
    }

    /**
     * Imports the entities registered in the DialogFlow project.
     * <p>
     * Entities import can be disabled to reduce the number of queries sent to the DialogFlow API by setting the
     * {@link DialogFlowConfiguration#ENABLE_ENTITY_LOADING_KEY} property to {@code false} in the provided
     * {@link Configuration}. Note that disabling entities import may generate consistency issues when creating,
     * deleting, and matching intents.
     */
    private void importRegisteredEntities() {
        this.registeredEntityTypes = new HashMap<>();
        if (this.configuration.isCleanAgentOnStartup()) {
            Log.info("Skipping entity types import, the agent has been cleaned on startup");
            return;
        }
        if (this.configuration.isEnableIntentLoader()) {
            Log.info("Loading Entities previously registered in the DialogFlow project {0}", projectName.getProject());
            for (EntityType entityType : getRegisteredEntityTypes()) {
                registeredEntityTypes.put(entityType.getDisplayName(), entityType);
            }
        } else {
            Log.info("Entity loading is disabled, existing Entities in the DialogFlow project {0} will not be " +
                    "imported", projectName.getProject());
        }
    }

    /**
     * Returns the description of the {@link EntityType}s that are registered in the DialogFlow project.
     *
     * @return the descriptions of the {@link EntityType}s that are registered in the DialogFlow project
     * @throws DialogFlowException if the {@link DialogFlowApi} is shutdown
     */
    private List<EntityType> getRegisteredEntityTypes() {
        checkNotShutdown();
        List<EntityType> registeredEntityTypes = new ArrayList<>();
        for (EntityType entityType :
                this.dialogFlowClients.getEntityTypesClient().listEntityTypes(projectAgentName).iterateAll()) {
            registeredEntityTypes.add(entityType);
        }
        return registeredEntityTypes;
    }

    /**
     * Returns the partial description of the {@link Intent}s that are registered in the DialogFlow project.
     * <p>
     * The partial descriptions of the {@link Intent}s does not include the {@code training phrases}.
     *
     * @return the partial descriptions of the {@link Intent}s that are registered in the DialogFlow project
     * @throws DialogFlowException if the {@link DialogFlowApi} is shutdown
     */
    private List<Intent> getRegisteredIntents() {
        checkNotShutdown();
        List<Intent> registeredIntents = new ArrayList<>();
        for (Intent intent : this.dialogFlowClients.getIntentsClient().listIntents(projectAgentName).iterateAll()) {
            registeredIntents.add(intent);
        }
        return registeredIntents;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method reuses the information contained in the provided {@link EntityDefinition} to create a new
     * DialogFlow {@link EntityType} and add it to the current project.
     *
     * @param entityDefinition the {@link EntityDefinition} to register to the DialogFlow project
     * @throws DialogFlowException if the {@link DialogFlowApi} is shutdown, or if the {@link EntityType} already
     *                             exists in the DialogFlow project
     */
    public void registerEntityDefinition(@NonNull EntityDefinition entityDefinition) {
        checkNotShutdown();
        if (entityDefinition instanceof BaseEntityDefinition) {
            BaseEntityDefinition baseEntityDefinition = (BaseEntityDefinition) entityDefinition;
            Log.trace("Skipping registration of {0} ({1}), {0} are natively supported by DialogFlow",
                    BaseEntityDefinition.class.getSimpleName(), baseEntityDefinition.getEntityType().getLiteral());
        } else if (entityDefinition instanceof CustomEntityDefinition) {
            Log.debug("Registering {0} {1}", CustomEntityDefinition.class.getSimpleName(), entityDefinition.getName());
            EntityType entityType = this.registeredEntityTypes.get(entityDefinition.getName());
            if (isNull(entityType)) {
                if (entityDefinition instanceof CompositeEntityDefinition) {
                    this.registerReferencedEntityDefinitions((CompositeEntityDefinition) entityDefinition);
                }
                entityType =
                        dialogFlowEntityMapper.mapEntityDefinition(entityDefinition);
                try {
                    /*
                     * Store the EntityType returned by the DialogFlow API: some fields such as the name are
                     * automatically set by the platform.
                     */
                    EntityType createdEntityType =
                            this.dialogFlowClients.getEntityTypesClient().createEntityType(projectAgentName,
                                    entityType);
                    this.registeredEntityTypes.put(entityDefinition.getName(), createdEntityType);
                } catch (FailedPreconditionException e) {
                    throw new DialogFlowException(MessageFormat.format("Cannot register the entity {0}, the entity " +
                            "already exists", entityDefinition), e);
                }
            } else {
                Log.debug("{0} {1} is already registered", EntityType.class.getSimpleName(),
                        entityDefinition.getName());
            }
        } else {
            throw new DialogFlowException(MessageFormat.format("Cannot register the provided {0}, unsupported {1}",
                    entityDefinition.getClass().getSimpleName(), EntityDefinition.class.getSimpleName()));
        }
    }

    /**
     * Registers the {@link EntityDefinition}s referred by the provided {@code compositeEntityDefinition}.
     * <p>
     * Note that this method only registers {@link CustomEntityDefinition}s referred from the provided {@code
     * compositeEntityDefinition}. {@link BaseEntityDefinition}s are already registered since they are part of the
     * platform.
     *
     * @param compositeEntityDefinition the {@link CompositeEntityDefinition} to register the referred
     *                                  {@link EntityDefinition}s of
     * @throws NullPointerException if the provided {@code compositeEntityDefinition} is {@code null}
     * @see #registerEntityDefinition(EntityDefinition)
     */
    private void registerReferencedEntityDefinitions(@NonNull CompositeEntityDefinition compositeEntityDefinition) {
        for (CompositeEntityDefinitionEntry entry : compositeEntityDefinition.getEntries()) {
            for (EntityDefinition referredEntityDefinition : entry.getEntities()) {
                if (referredEntityDefinition instanceof CustomEntityDefinition) {
                    /*
                     * Only register CustomEntityDefinitions, the other ones are already part of the system.
                     */
                    try {
                        this.registerEntityDefinition(referredEntityDefinition);
                    } catch (DialogFlowException e) {
                        /*
                         * Simply log a warning here, the entity may have been registered before.
                         */
                        Log.warn(e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method reuses the information contained in the provided {@link IntentDefinition} to create a new
     * DialogFlow {@link Intent} and add it to the current project.
     *
     * @param intentDefinition the {@link IntentDefinition} to register to the DialogFlow project
     * @throws DialogFlowException if the {@link DialogFlowApi} is shutdown, or if the {@link Intent} already exists in
     *                             the DialogFlow project
     * @see DialogFlowIntentMapper
     */
    @Override
    public void registerIntentDefinition(@NonNull IntentDefinition intentDefinition) {
        checkNotShutdown();
        checkNotNull(intentDefinition.getName(), "Cannot register the %s with the provided name %s",
                IntentDefinition.class.getSimpleName());
        if (this.registeredIntents.containsKey(intentDefinition.getName())) {
            throw new DialogFlowException(MessageFormat.format("Cannot register the intent {0}, the intent " +
                    "already exists", intentDefinition.getName()));
        }
        Log.debug("Registering DialogFlow intent {0}", intentDefinition.getName());
        Intent intent = dialogFlowIntentMapper.mapIntentDefinition(intentDefinition);
        try {
            Intent response = this.dialogFlowClients.getIntentsClient().createIntent(projectAgentName, intent);
            registeredIntents.put(response.getDisplayName(), response);
            Log.debug("Intent {0} successfully registered", response.getDisplayName());
        } catch (FailedPreconditionException | InvalidArgumentException e) {
            if (e.getMessage().contains("already exists")) {
                throw new DialogFlowException(MessageFormat.format("Cannot register the intent {0}, the intent " +
                        "already exists", intentDefinition.getName()), e);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws DialogFlowException if the {@link DialogFlowApi} is shutdown
     */
    @Override
    public void deleteEntityDefinition(@NonNull EntityDefinition entityDefinition) {
        checkNotShutdown();
        if (entityDefinition instanceof BaseEntityDefinition) {
            BaseEntityDefinition baseEntityDefinition = (BaseEntityDefinition) entityDefinition;
            Log.trace("Skipping deletion of {0} ({1}), {0} are natively supported by DialogFlow and cannot be " +
                    "deleted", BaseEntityDefinition.class.getSimpleName(), baseEntityDefinition.getEntityType()
                    .getLiteral());
        } else if (entityDefinition instanceof CustomEntityDefinition) {
            CustomEntityDefinition customEntityDefinition = (CustomEntityDefinition) entityDefinition;
            /*
             * Reduce the number of calls to the DialogFlow API by first looking for the EntityType in the local cache.
             */
            EntityType entityType = this.registeredEntityTypes.get(customEntityDefinition.getName());
            if (isNull(entityType)) {
                /*
                 * The EntityType is not in the local cache, loading it through a DialogFlow query.
                 */
                Optional<EntityType> dialogFlowEntityType = getRegisteredEntityTypes().stream().filter
                        (registeredEntityType -> registeredEntityType.getDisplayName().equals(customEntityDefinition
                                .getName())).findAny();
                if (dialogFlowEntityType.isPresent()) {
                    entityType = dialogFlowEntityType.get();
                } else {
                    Log.warn("Cannot delete the {0} {1}, the entity type does not exist", EntityType.class
                            .getSimpleName(), entityDefinition.getName());
                    return;
                }
            }
            try {
                this.dialogFlowClients.getEntityTypesClient().deleteEntityType(entityType.getName());
            } catch(InvalidArgumentException e) {
                throw new DialogFlowException(MessageFormat.format("An error occurred while deleting entity {0}",
                        entityDefinition.getName()), e);
            }
            Log.debug("{0} {1} successfully deleted", EntityType.class.getSimpleName(), entityType.getDisplayName());
            /*
             * Remove the deleted EntityType from the local cache.
             */
            this.registeredEntityTypes.remove(entityType.getDisplayName());
        } else {
            throw new DialogFlowException(MessageFormat.format("Cannot delete the provided {0}, unsupported {1}",
                    entityDefinition.getClass().getSimpleName(), EntityDefinition.class.getSimpleName()));
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws DialogFlowException if the {@link DialogFlowApi} is shutdown
     */
    @Override
    public void deleteIntentDefinition(@NonNull IntentDefinition intentDefinition) {
        checkNotShutdown();
        checkNotNull(intentDefinition.getName(), "Cannot delete the IntentDefinition with null as its name");
        /*
         * Reduce the number of calls to the DialogFlow API by first looking for the Intent in the local cache.
         */
        Intent intent = this.registeredIntents.get(intentDefinition.getName());
        if (isNull(intent)) {
            /*
             * The Intent is not in the local cache, loading it through a DialogFlow query.
             */
            Optional<Intent> dialogFlowIntent = getRegisteredIntents().stream().filter(registeredIntent ->
                    registeredIntent.getDisplayName().equals(intentDefinition.getName())).findAny();
            if (dialogFlowIntent.isPresent()) {
                intent = dialogFlowIntent.get();
            } else {
                Log.warn("Cannot delete the {0} {1}, the intent does not exist", Intent.class.getSimpleName(),
                        intentDefinition.getName());
                return;
            }
        }
        this.dialogFlowClients.getIntentsClient().deleteIntent(intent.getName());
        Log.debug("{0} {1} successfully deleted", Intent.class.getSimpleName(), intentDefinition.getName());
        /*
         * Remove the deleted Intent from the local cache.
         */
        this.registeredIntents.remove(intent.getDisplayName());
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method checks every second whether the underlying ML Engine has finished its training. Note that this
     * method is blocking as long as the ML Engine training is not terminated, and may not terminate if an issue
     * occurred on the DialogFlow side.
     *
     * @throws DialogFlowException if the {@link DialogFlowApi} is shutdown
     */
    @Override
    public void trainMLEngine() {
        checkNotShutdown();
        Log.info("Starting ML Engine Training (this may take a few minutes)");
        TrainAgentRequest request = TrainAgentRequest.newBuilder()
                .setParent(projectName.toString())
                .build();
        ApiFuture<Operation> future = this.dialogFlowClients.getAgentsClient().trainAgentCallable().futureCall(request);
        try {
            Operation operation = future.get();
            while (!operation.getDone()) {
                Thread.sleep(1000);
                /*
                 * Retrieve the new version of the Operation from the API.
                 */
                operation =
                        this.dialogFlowClients.getAgentsClient().getOperationsClient().getOperation(operation.getName());
            }
            Log.info("ML Engine Training completed");
        } catch (InterruptedException | ExecutionException e) {
            String errorMessage = "An error occurred during the ML Engine Training";
            Log.error(errorMessage);
            throw new DialogFlowException(errorMessage, e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The created session wraps the internal DialogFlow session that is used on the DialogFlow project to retrieve
     * conversation parts from a given user.
     * <p>
     * The returned {@link XatkitSession} is configured by the global {@link Configuration} provided in
     * {@link #DialogFlowApi(EventDefinitionRegistry, Configuration)}.
     *
     * @throws DialogFlowException if the {@link DialogFlowApi} is shutdown
     */
    @Override
    public XatkitSession createSession(@NonNull String sessionId) {
        checkNotShutdown();
        SessionName sessionName = SessionName.of(this.configuration.getProjectId(), sessionId);
        return new DialogFlowSession(sessionName, this.configuration.getBaseConfiguration());
    }

    /**
     * Merges the local {@link DialogFlowSession} in the remote DialogFlow API one.
     * <p>
     * This method ensures that the remote DialogFlow API stays consistent with the local {@link XatkitSession} by
     * setting all the local context variables in the remote session. This allows to match intents with input
     * contexts that have been defined locally, such as received events, custom variables, etc.
     * <p>
     * Local context values that are already defined in the remote DialogFlow API will be overridden by this method.
     * <p>
     * This method sets all the variables from the local context in a single query in order to reduce the number of
     * calls to the remote DialogFlow API.
     *
     * @param dialogFlowSession the local {@link DialogFlowSession} to merge in the remote one
     * @throws XatkitException      if at least one of the local context values' type is not supported
     * @throws NullPointerException if the provided {@code dialogFlowSession} is {@code null}
     * @see #getIntent(String, XatkitSession)
     */
    public void mergeLocalSessionInDialogFlow(DialogFlowSession dialogFlowSession) {
        Log.debug("Merging local context in the DialogFlow session {0}", dialogFlowSession.getSessionId());
        checkNotNull(dialogFlowSession, "Cannot merge the provided %s %s", DialogFlowSession.class.getSimpleName(),
                dialogFlowSession);
        dialogFlowSession.getRuntimeContexts().getContextMap().entrySet().stream().forEach(contextEntry ->
                {
                    String contextName = contextEntry.getKey();
                    int contextLifespanCount = dialogFlowSession.getRuntimeContexts().getContextLifespanCount
                            (contextName);
                    Context.Builder builder =
                            Context.newBuilder().setName(ContextName.of(this.configuration.getProjectId(),
                                    dialogFlowSession.getSessionName().getSession(), contextName).toString());
                    Map<String, Object> contextVariables = contextEntry.getValue();
                    Map<String, Value> dialogFlowContextVariables = new HashMap<>();
                    contextVariables.entrySet().stream().forEach(contextVariableEntry -> {
                        Value value = buildValue(contextVariableEntry.getValue());
                        dialogFlowContextVariables.put(contextVariableEntry.getKey(), value);
                    });
                    /*
                     * Need to put the lifespanCount otherwise the context is ignored.
                     */
                    builder.setParameters(Struct.newBuilder().putAllFields(dialogFlowContextVariables))
                            .setLifespanCount(contextLifespanCount);
                    this.dialogFlowClients.getContextsClient().createContext(dialogFlowSession.getSessionName(),
                            builder.build());
                }
        );
    }

    /**
     * Creates a protobuf {@link Value} from the provided {@link Object}.
     * <p>
     * This method supports {@link String} and {@link Map} as input, other data types should not be passed to this
     * method, because all the values returned by DialogFlow are translated into {@link String} or {@link Map}.
     *
     * @param from the {@link Object} to translate to a protobuf {@link Value}
     * @return the protobuf {@link Value}
     * @throws IllegalArgumentException if the provided {@link Object}'s type is not supported
     * @see #buildStruct(Map)
     */
    private Value buildValue(Object from) {
        Value.Builder valueBuilder = Value.newBuilder();
        if (from instanceof String) {
            valueBuilder.setStringValue((String) from);
            return valueBuilder.build();
        } else if (from instanceof Map) {
            Struct struct = buildStruct((Map<String, Object>) from);
            return valueBuilder.setStructValue(struct).build();
        } else {
            throw new IllegalArgumentException(MessageFormat.format("Cannot build a protobuf value from {0}", from));
        }
    }

    /**
     * Creates a protobuf {@link Struct} from the provided {@link Map}.
     * <p>
     * This method deals with nested {@link Map}s, as long as their values are {@link String}s. The returned
     * {@link Struct} reflects the {@link Map} nesting hierarchy.
     *
     * @param fromMap the {@link Map} to translate to a protobuf {@link Struct}
     * @return the protobuf {@link Struct}
     * @throws IllegalArgumentException if a nested {@link Map}'s value type is not {@link String} or {@link Map}
     */
    private Struct buildStruct(Map<String, Object> fromMap) {
        Struct.Builder structBuilder = Struct.newBuilder();
        for (Map.Entry<String, Object> entry : fromMap.entrySet()) {
            if (entry.getValue() instanceof String) {
                structBuilder.putFields(entry.getKey(),
                        Value.newBuilder().setStringValue((String) entry.getValue()).build());
            } else if (entry.getValue() instanceof Map) {
                structBuilder.putFields(entry.getKey(), Value.newBuilder().setStructValue(buildStruct((Map<String,
                        Object>) entry.getValue())).build());
            } else {
                throw new IllegalArgumentException(MessageFormat.format("Cannot build a protobuf struct " +
                        "from {0}, unsupported data type", fromMap));
            }
        }
        return structBuilder.build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned {@link RecognizedIntent} is constructed from the raw {@link Intent} returned by the DialogFlow
     * API, using the mapping defined in {@link RecognizedIntentMapper}.
     * <p>
     * If the {@link DialogFlowConfiguration#ENABLE_LOCAL_CONTEXT_MERGE_KEY} property is set to {@code true} this
     * method will first merge the local {@link XatkitSession} in the remote DialogFlow one, in order to ensure that
     * all the local contexts are propagated to the recognition engine.
     *
     * @throws NullPointerException     if the provided {@code input} or {@code session} is {@code null}
     * @throws IllegalArgumentException if the provided {@code input} is empty
     * @throws DialogFlowException      if the {@link DialogFlowApi} is shutdown or if an exception is thrown by the
     *                                  underlying DialogFlow engine
     */
    @Override
    public RecognizedIntent getIntentInternal(@NonNull String input, @NonNull XatkitSession session) {
        checkNotShutdown();
        checkArgument(!input.isEmpty(), "Cannot retrieve the intent from empty string");
        checkArgument(session instanceof DialogFlowSession, "Cannot handle the message, expected session type to be " +
                "%s, found %s", DialogFlowSession.class.getSimpleName(), session.getClass().getSimpleName());
        TextInput.Builder textInput =
                TextInput.newBuilder().setText(input).setLanguageCode(this.configuration.getLanguageCode());
        QueryInput queryInput = QueryInput.newBuilder().setText(textInput).build();
        DetectIntentResponse response;

        DialogFlowSession dialogFlowSession = (DialogFlowSession) session;
        if (this.configuration.isEnableContextMerge()) {
            mergeLocalSessionInDialogFlow(dialogFlowSession);
        } else {
            Log.debug("Local context not merged in DialogFlow, context merging has been disabled");
        }

        try {
            response =
                    this.dialogFlowClients.getSessionsClient().detectIntent(((DialogFlowSession) session).getSessionName(),
                            queryInput);
        } catch (Exception e) {
            throw new DialogFlowException(e);
        }
        QueryResult queryResult = response.getQueryResult();
        RecognizedIntent recognizedIntent = recognizedIntentMapper.mapQueryResult(queryResult);
        if (nonNull(recognitionMonitor)) {
            recognitionMonitor.logRecognizedIntent(session, recognizedIntent);
        }
        return recognizedIntent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        checkNotShutdown();
        this.dialogFlowClients.shutdown();
        if (nonNull(this.recognitionMonitor)) {
            this.recognitionMonitor.shutdown();
        }
    }

    /**
     * Throws a {@link DialogFlowException} if the provided {@link DialogFlowApi} is shutdown.
     * <p>
     * This method is typically called in methods that need to interact with the DialogFlow API, and cannot complete
     * if the connector is shutdown.
     *
     * @throws DialogFlowException  if the provided {@code dialogFlowApi} is shutdown
     * @throws NullPointerException if the provided {@code dialogFlowApi} is {@code null}
     */
    private void checkNotShutdown() throws DialogFlowException {
        if (this.isShutdown()) {
            throw new DialogFlowException("Cannot perform the operation, the DialogFlow API is shutdown");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public RecognitionMonitor getRecognitionMonitor() {
        return recognitionMonitor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isShutdown() {
        return this.dialogFlowClients.isShutdown();
    }
}
