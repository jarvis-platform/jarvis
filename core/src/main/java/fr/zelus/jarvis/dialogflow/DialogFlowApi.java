package fr.zelus.jarvis.dialogflow;

import com.google.cloud.dialogflow.v2.*;
import fr.inria.atlanmod.commons.log.Log;
import fr.zelus.jarvis.core.JarvisCore;
import fr.zelus.jarvis.intent.IntentDefinition;
import fr.zelus.jarvis.intent.IntentFactory;
import fr.zelus.jarvis.intent.RecognizedIntent;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.UUID;

import static fr.inria.atlanmod.commons.Preconditions.checkArgument;
import static fr.inria.atlanmod.commons.Preconditions.checkNotNull;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * A wrapper of the DialogFlow API that provides utility methods to connect to a given DialogFlow project, start
 * sessions, and detect intents from textual inputs.
 * <p>
 * This class is used to easily setup a connection to a given DialogFlow project. Note that in addition to the
 * constructor parameters, the {@code GOOGLE_APPLICATION_CREDENTIALS} environment variable must be set and point to
 * the DialogFlow project's key. See
 * <a href="https://cloud.google.com/dialogflow-enterprise/docs/reference/libraries">DialogFlow documentation</a> for
 * further information.
 */
public class DialogFlowApi {

    /**
     * The default language processed by DialogFlow.
     */
    private static String DEFAULT_LANGUAGE_CODE = "en-US";

    /**
     * The unique identifier of the DialogFlow project.
     */
    private String projectId;

    /**
     * The language code of the DialogFlow project.
     */
    private String languageCode;

    /**
     * The client instance managing DialogFlow sessions.
     * <p>
     * This instance is used to initiate new sessions (see {@link #createSession()}) and send {@link Intent}
     * detection queries to the DialogFlow engine.
     */
    private SessionsClient sessionsClient;

    /**
     * The {@link IntentFactory} used to create {@link RecognizedIntent} instances from DialogFlow computed
     * {@link Intent}s.
     */
    private IntentFactory intentFactory;

    /**
     * Constructs a {@link DialogFlowApi} with the provided {@code projectId} and sets its language to
     * {@link #DEFAULT_LANGUAGE_CODE}.
     * <p>
     * See {@link #DialogFlowApi(String, String)} to construct a {@link DialogFlowApi} instance with a given {@code
     * languageCode}.
     *
     * @param projectId the unique identifier of the DialogFlow project
     * @throws NullPointerException if the provided {@code projectId} or {@code languageCode} is
     *                              {@code null}.
     * @throws DialogFlowException  if the client failed to start a new session
     * @see #DialogFlowApi(String, String)
     */
    public DialogFlowApi(String projectId) {
        this(projectId, DEFAULT_LANGUAGE_CODE);
    }

    /**
     * Constructs a {@link DialogFlowApi} with the provided {@code projectId} and {@code languageCode}.
     *
     * @param projectId    the unique identifier of the DialogFlow project
     * @param languageCode the code of the language processed by DialogFlow
     * @throws NullPointerException if the provided {@code projectId} or {@code languageCode} is
     *                              {@code null}.
     * @throws DialogFlowException  if the client failed to start a new session
     */
    public DialogFlowApi(String projectId, String languageCode) {
        checkNotNull(projectId, "Cannot construct a DialogFlow API instance from a null project ID");
        checkNotNull(languageCode, "Cannot construct a DialogFlow API instance from a null language code");
        Log.info("Creating a new DialogFlowAPI");
        try {
            Log.info("Starting DialogFlow Client");
            this.projectId = projectId;
            this.languageCode = languageCode;
            this.sessionsClient = SessionsClient.create();
            this.intentFactory = IntentFactory.eINSTANCE;
        } catch (IOException e) {
            throw new DialogFlowException("Cannot construct the DialogFlow API", e);
        }
    }

    /**
     * Returns the DialogFlow project unique identifier.
     *
     * @return the DialogFlow project unique identifier
     */
    public String getProjectId() {
        return projectId;
    }

    /**
     * Returns the code of the language processed by DialogFlow.
     *
     * @return the code of the language processed by DialogFlow
     */
    public String getLanguageCode() {
        return languageCode;
    }

    /**
     * Creates a new DialogFlow session.
     * <p>
     * A DialogFlow session contains contextual information and previous answers for a given client, and should not
     * be shared between clients.
     *
     * @return a {@link SessionName} identifying the created session
     * @throws DialogFlowException if the {@link DialogFlowApi} is shutdown
     */
    public SessionName createSession() {
        if (isShutdown()) {
            throw new DialogFlowException("Cannot create a new Session, the DialogFlow API is shutdown");
        }
        UUID identifier = UUID.randomUUID();
        SessionName session = SessionName.of(projectId, identifier.toString());
        Log.info("New session created with path {0}", session.toString());
        return session;
    }

    /**
     * Shuts down the DialogFlow client and invalidates the session.
     * <p>
     * <b>Note:</b> calling this method invalidates the DialogFlow connection, and thus this class cannot be used to
     * access DialogFlow API anymore.
     */
    public void shutdown() {
        if(isShutdown()) {
            throw new DialogFlowException("Cannot perform shutdown, DialogFlow API is already shutdown");
        }
        this.sessionsClient.shutdownNow();
    }

    /**
     * Returns whether the DialogFlow client is shutdown.
     *
     * @return {@code true} if the DialogFlow client is shutdown, {@code false} otherwise
     */
    public boolean isShutdown() {
        return this.sessionsClient.isShutdown();
    }

    /**
     * Returns the {@link RecognizedIntent} extracted from the provided {@code text}
     * <p>
     * The returned {@link RecognizedIntent} is constructed from the raw {@link Intent} returned by the DialogFlow
     * API, using the mapping defined in {@link #convertDialogFlowIntent(Intent)}. {@link RecognizedIntent}s are used
     * to wrap the Intents returned by the Intent Recognition APIs and decouple the application from the concrete API
     * used.
     * <p>
     * This method uses the provided {@code session} to extract contextual {@link Intent}s, such as follow-up
     * or context-based {@link Intent}s.
     *
     * @param text    a {@link String} representing the textual input to process and extract the {@link Intent} from
     * @param session the client {@link SessionName}
     * @return a {@link RecognizedIntent} extracted from the provided input {@code text}
     * @throws NullPointerException     if the provided {@code text} or {@code session} is {@code null}
     * @throws IllegalArgumentException if the provided {@code text} is empty
     * @throws DialogFlowException      if the {@link DialogFlowApi} is shutdown or if an exception is thrown by the
     *                                  underlying DialogFlow engine
     */
    public RecognizedIntent getIntent(String text, SessionName session) {
        if (isShutdown()) {
            throw new DialogFlowException("Cannot extract an Intent from the provided input, the DialogFlow API is " +
                    "shutdown");
        }
        checkNotNull(text, "Cannot retrieve the intent from null");
        checkNotNull(session, "Cannot retrieve the intent using null as a session");
        checkArgument(!text.isEmpty(), "Cannot retrieve the intent from empty string");
        TextInput.Builder textInput = TextInput.newBuilder().setText(text).setLanguageCode(languageCode);
        QueryInput queryInput = QueryInput.newBuilder().setText(textInput).build();
        DetectIntentResponse response;
        try {
            response = sessionsClient.detectIntent(session, queryInput);
        } catch (Exception e) {
            throw new DialogFlowException(e);
        }
        QueryResult queryResult = response.getQueryResult();
        Log.info("====================\n" +
                "Query Text: {0} \n" +
                "Detected Intent: {1} (confidence: {2})\n" +
                "Fulfillment Text: {3}", queryResult.getQueryText(), queryResult.getIntent()
                .getDisplayName(), queryResult.getIntentDetectionConfidence(), queryResult.getFulfillmentText());
        return convertDialogFlowIntent(queryResult.getIntent());
    }

    private RecognizedIntent convertDialogFlowIntent(Intent intent) {
        if(nonNull(intent)) {
            RecognizedIntent recognizedIntent = intentFactory.createRecognizedIntent();
            /*
             * Retrieve the IntentDefinition corresponding to this Intent.
             */
            IntentDefinition intentDefinition = JarvisCore.getInstance().getIntentDefinitionRegistry()
                    .getIntentDefinition(intent.getDisplayName());
            if(isNull(intentDefinition)) {
                String errorMessage = MessageFormat.format("Cannot retrieve the IntentDefinition associated to the " +
                        "provided DialogFlow Intent {0}", intent.getDisplayName());
                Log.error(errorMessage);
                throw new DialogFlowException(errorMessage);
            }
            recognizedIntent.setDefinition(intentDefinition);
            /*
             * Set the output context values.
             */
            if(intent.getOutputContextsCount() > 0) {
                if(intent.getOutputContextsCount() > 1) {
                    Log.warn("Multiple output contexts are not supported for now, proceeding with the first context " +
                            "found");
                }
                Context outContext = intent.getOutputContexts(0);
                Collection<Object> outContextValues = outContext.getParameters().getAllFields().values();
                for(Object value : outContextValues) {
                    if(value instanceof String) {
                        recognizedIntent.getOutContextValues().add((String) value);
                    } else {
                        throw new UnsupportedOperationException("Only String output context values are supported for " +
                                "now");
                    }
                }
                return recognizedIntent;
            } else {
                return recognizedIntent;
            }
        } else {
            Log.warn("Cannot convert null to a RecognizedIntent");
            return null;
        }
    }

    /**
     * Closes the DialogFlow session if it is not shutdown yet.
     *
     * @throws Throwable
     */
    @Override
    protected void finalize() throws Throwable {
        if (!sessionsClient.isShutdown()) {
            Log.warn("DialogFlow session was not closed properly, calling automatic shutdown");
            this.sessionsClient.shutdownNow();
        }
    }
}