package fr.zelus.jarvis.dialogflow;

import com.google.cloud.dialogflow.v2.Intent;
import fr.zelus.jarvis.core.JarvisCore;
import fr.zelus.jarvis.core.session.JarvisSession;
import fr.zelus.jarvis.intent.IntentDefinition;
import fr.zelus.jarvis.intent.IntentFactory;
import fr.zelus.jarvis.intent.RecognizedIntent;
import fr.zelus.jarvis.module.Action;
import fr.zelus.jarvis.module.InputProviderDefinition;
import fr.zelus.jarvis.module.Module;
import fr.zelus.jarvis.module.ModuleFactory;
import fr.zelus.jarvis.orchestration.ActionInstance;
import fr.zelus.jarvis.orchestration.OrchestrationFactory;
import fr.zelus.jarvis.orchestration.OrchestrationLink;
import fr.zelus.jarvis.orchestration.OrchestrationModel;
import fr.zelus.jarvis.util.VariableLoaderHelper;
import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.*;

import java.util.List;
import java.util.UUID;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class DialogFlowApiTest {

    protected static String VALID_PROJECT_ID = VariableLoaderHelper.getJarvisDialogFlowProject();

    protected static String VALID_LANGUAGE_CODE = "en-US";

    protected static String SAMPLE_INPUT = "hello";

    protected DialogFlowApi api;

    /**
     * Stores the last {@link IntentDefinition} registered by
     * {@link DialogFlowApi#registerIntentDefinition(IntentDefinition)}.
     * <p>
     * <b>Note:</b> this variable must be set by each test case calling
     * {@link DialogFlowApi#registerIntentDefinition(IntentDefinition)}, to enable their deletion in the
     * {@link #tearDown()} method. Not setting this variable would add test-related intents in the DialogFlow project.
     *
     * @see #tearDown()
     */
    private IntentDefinition registeredIntentDefinition;

    // not tested here, only instantiated to enable IntentDefinition registration and Module retrieval
    protected static JarvisCore jarvisCore;

    private static Configuration buildConfiguration(String projectId, String languageCode) {
        Configuration configuration = new BaseConfiguration();
        configuration.addProperty(DialogFlowApi.PROJECT_ID_KEY, projectId);
        configuration.addProperty(DialogFlowApi.LANGUAGE_CODE_KEY, languageCode);
        return configuration;
    }

    @BeforeClass
    public static void setUpBeforeClass() {
        Module stubModule = ModuleFactory.eINSTANCE.createModule();
        stubModule.setName("StubJarvisModule");
        stubModule.setJarvisModulePath("fr.zelus.jarvis.stubs.StubJarvisModule");
        Action stubAction = ModuleFactory.eINSTANCE.createAction();
        stubAction.setName("StubJarvisAction");
        // No parameters, keep it simple
        stubModule.getActions().add(stubAction);
        InputProviderDefinition stubInputProvider = ModuleFactory.eINSTANCE.createInputProviderDefinition();
        stubInputProvider.setName("StubInputProvider");
        stubModule.getEventProviderDefinitions().add(stubInputProvider);
        IntentDefinition stubIntentDefinition = IntentFactory.eINSTANCE.createIntentDefinition();
        stubIntentDefinition.setName("Default Welcome Intent");
        // No parameters, keep it simple
        stubModule.getIntentDefinitions().add(stubIntentDefinition);
        OrchestrationModel orchestrationModel = OrchestrationFactory.eINSTANCE.createOrchestrationModel();
        OrchestrationLink link = OrchestrationFactory.eINSTANCE.createOrchestrationLink();
        link.setEvent(stubIntentDefinition);
        ActionInstance actionInstance = OrchestrationFactory.eINSTANCE.createActionInstance();
        actionInstance.setAction(stubAction);
        link.getActions().add(actionInstance);
        orchestrationModel.getOrchestrationLinks().add(link);
        Configuration configuration = buildConfiguration(VALID_PROJECT_ID, VALID_LANGUAGE_CODE);
        configuration.addProperty(JarvisCore.ORCHESTRATION_MODEL_KEY, orchestrationModel);
        jarvisCore = new JarvisCore(configuration);
    }

    @After
    public void tearDown() {
        if (nonNull(registeredIntentDefinition)) {
            api.deleteIntentDefinition(registeredIntentDefinition);
        }
        /*
         * Reset the variable value to null to avoid unnecessary deletion calls.
         */
        registeredIntentDefinition = null;
        if (nonNull(api)) {
            try {
                api.shutdown();
            } catch (DialogFlowException e) {
                /*
                 * Already shutdown, ignore
                 */
            }
        }
    }

    @AfterClass
    public static void tearDownAfterClass() {
        jarvisCore.shutdown();
    }

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    private DialogFlowApi getValidDialogFlowApi() {
        api = new DialogFlowApi(jarvisCore, buildConfiguration(VALID_PROJECT_ID, VALID_LANGUAGE_CODE));
        return api;
    }

    @Test(expected = NullPointerException.class)
    public void constructNullJarvisCore() {
        api = new DialogFlowApi(null, buildConfiguration(VALID_PROJECT_ID, VALID_LANGUAGE_CODE));
    }

    @Test(expected = NullPointerException.class)
    public void constructNullProjectIdValidLanguageCode() {
        api = new DialogFlowApi(null, buildConfiguration(null, "en-US"));
    }

    @Test
    public void constructNullLanguageCode() {
        api = new DialogFlowApi(jarvisCore, buildConfiguration(VALID_PROJECT_ID, null));
        assertThat(api.getLanguageCode()).as("Default language code").isEqualTo(DialogFlowApi.DEFAULT_LANGUAGE_CODE);
    }

    @Test
    public void constructValid() {
        api = new DialogFlowApi(jarvisCore, buildConfiguration(VALID_PROJECT_ID, VALID_LANGUAGE_CODE));
        softly.assertThat(VALID_PROJECT_ID).as("Valid project ID").isEqualTo(api.getProjectId());
        softly.assertThat(VALID_LANGUAGE_CODE).as("Valid language code").isEqualTo(api.getLanguageCode());
        softly.assertThat(api.isShutdown()).as("Not shutdown").isFalse();
    }

    @Test
    public void constructDefaultLanguageCode() {
        api = new DialogFlowApi(jarvisCore, buildConfiguration(VALID_PROJECT_ID, null));
        softly.assertThat(VALID_PROJECT_ID).as("Valid project ID").isEqualTo(api.getProjectId());
        softly.assertThat(VALID_LANGUAGE_CODE).as("Valid language code").isEqualTo(api.getLanguageCode());
    }

    @Test(expected = NullPointerException.class)
    public void registerIntentDefinitionNullIntentDefinition() {
        api = getValidDialogFlowApi();
        api.registerIntentDefinition(null);
    }

    @Test(expected = NullPointerException.class)
    public void registerIntentDefinitionNullName() {
        api = getValidDialogFlowApi();
        IntentDefinition intentDefinition = IntentFactory.eINSTANCE.createIntentDefinition();
        api.registerIntentDefinition(intentDefinition);
    }

    @Test
    public void registerIntentDefinitionValidIntentDefinition() {
        api = getValidDialogFlowApi();
        registeredIntentDefinition = IntentFactory.eINSTANCE.createIntentDefinition();
        String intentName = UUID.randomUUID().toString();
        String trainingPhrase = "test";
        registeredIntentDefinition.setName(intentName);
        registeredIntentDefinition.getTrainingSentences().add(trainingPhrase);
        api.registerIntentDefinition(registeredIntentDefinition);
        List<Intent> registeredIntents = api.getRegisteredIntentsFullView();
        assertThat(registeredIntents).as("Registered Intent list is not null").isNotNull();
        softly.assertThat(registeredIntents).as("Registered Intent list is not empty").isNotEmpty();
        Intent foundIntent = null;
        for (Intent intent : registeredIntents) {
            if (intent.getDisplayName().equals(intentName)) {
                foundIntent = intent;
            }
        }
        assertThat(foundIntent).as("Registered Intent list contains the registered IntentDefinition")
                .isNotNull();
        softly.assertThat(foundIntent.getTrainingPhrasesList()).as("Intent's training phrase list is not empty")
                .isNotEmpty();
        boolean foundTrainingPhrase = false;
        for (Intent.TrainingPhrase intentTrainingPhrase : foundIntent.getTrainingPhrasesList()) {
            for (Intent.TrainingPhrase.Part part : intentTrainingPhrase.getPartsList()) {
                if (part.getText().equals(trainingPhrase)) {
                    foundTrainingPhrase = true;
                }
            }
        }
        softly.assertThat(foundTrainingPhrase).as("The IntentDefinition's training phrase is in the retrieved " +
                "Intent").isTrue();
    }

    @Test(expected = DialogFlowException.class)
    public void registerIntentDefinitionAlreadyRegistered() {
        api = getValidDialogFlowApi();
        registeredIntentDefinition = IntentFactory.eINSTANCE.createIntentDefinition();
        String intentName = UUID.randomUUID().toString();
        registeredIntentDefinition.setName(intentName);
        registeredIntentDefinition.getTrainingSentences().add("test");
        registeredIntentDefinition.getTrainingSentences().add("test jarvis");
        api.registerIntentDefinition(registeredIntentDefinition);
        api.registerIntentDefinition(registeredIntentDefinition);
    }

    @Test(expected = NullPointerException.class)
    public void deleteIntentDefinitionNullIntentDefinition() {
        api = getValidDialogFlowApi();
        api.deleteIntentDefinition(null);
    }

    @Test(expected = NullPointerException.class)
    public void deleteIntentDefinitionNullName() {
        api = getValidDialogFlowApi();
        IntentDefinition intentDefinition = IntentFactory.eINSTANCE.createIntentDefinition();
        api.deleteIntentDefinition(intentDefinition);
    }

    @Test
    public void deleteIntentDefinitionNotRegisteredIntent() {
        api = getValidDialogFlowApi();
        String intentName = UUID.randomUUID().toString();
        IntentDefinition intentDefinition = IntentFactory.eINSTANCE.createIntentDefinition();
        intentDefinition.setName(intentName);
        int registeredIntentsCount = api.getRegisteredIntents().size();
        api.deleteIntentDefinition(intentDefinition);
        assertThat(api.getRegisteredIntents()).as("Registered Intents list has not changed").hasSize
                (registeredIntentsCount);
    }

    @Test
    public void deleteIntentDefinitionRegisteredIntentDefinition() {
        api = getValidDialogFlowApi();
        String intentName = UUID.randomUUID().toString();
        registeredIntentDefinition = IntentFactory.eINSTANCE.createIntentDefinition();
        registeredIntentDefinition.setName(intentName);
        api.registerIntentDefinition(registeredIntentDefinition);
        api.deleteIntentDefinition(registeredIntentDefinition);
        Intent foundIntent = null;
        for (Intent intent : api.getRegisteredIntents()) {
            if (intent.getDisplayName().equals(intentName)) {
                foundIntent = intent;
            }
        }
        assertThat(foundIntent).as("The Intent has been removed from the registered Intents").isNull();

    }

    @Test
    public void createSessionValidApi() {
        api = getValidDialogFlowApi();
        JarvisSession session = api.createSession("sessionID");
        assertThat(session).as("Not null session").isNotNull();
        assertThat(session).as("The session is a DialogFlowSession instance").isInstanceOf(DialogFlowSession.class);
        DialogFlowSession dialogFlowSession = (DialogFlowSession) session;
        assertThat(dialogFlowSession.getSessionName()).as("Not null SessionName").isNotNull();
        assertThat(dialogFlowSession.getSessionName().getProject()).as("Valid session project").isEqualTo
                (VALID_PROJECT_ID);
    }

    @Test(expected = DialogFlowException.class)
    public void shutdownAlreadyShutdown() {
        api = getValidDialogFlowApi();
        api.shutdown();
        api.shutdown();
    }

    @Test
    public void shutdown() {
        api = getValidDialogFlowApi();
        JarvisSession session = api.createSession("sessionID");
        api.shutdown();
        softly.assertThat(api.isShutdown()).as("DialogFlow API is shutdown").isTrue();
        assertThatExceptionOfType(DialogFlowException.class).isThrownBy(() -> api.getIntent("test", session))
                .withMessage("Cannot extract an Intent from the provided input, the DialogFlow API is shutdown");
        assertThatExceptionOfType(DialogFlowException.class).isThrownBy(() -> api.createSession("sessionID"))
                .withMessage
                        ("Cannot create a new Session, the DialogFlow API is shutdown");
    }

    @Test
    public void getIntentValidSession() {
        api = getValidDialogFlowApi();
        JarvisSession session = api.createSession("sessionID");
        RecognizedIntent intent = api.getIntent(SAMPLE_INPUT, session);
        IntentDefinition intentDefinition = (IntentDefinition) intent.getDefinition();
        assertThat(intent).as("Null Intent").isNotNull();
        assertThat(intentDefinition).as("Null Intent Definition").isNotNull();
        assertThat(intentDefinition.getName()).as("Valid Intent").isEqualTo("Default Welcome Intent");
    }

    @Test(expected = DialogFlowException.class)
    public void getIntentInvalidSession() {
        api = new DialogFlowApi(jarvisCore, buildConfiguration("test", null));
        JarvisSession session = api.createSession("sessionID");
        RecognizedIntent intent = api.getIntent(SAMPLE_INPUT, session);
    }

    @Test(expected = NullPointerException.class)
    public void getIntentNullSession() {
        api = getValidDialogFlowApi();
        RecognizedIntent intent = api.getIntent(SAMPLE_INPUT, null);
    }

    @Test(expected = NullPointerException.class)
    public void getIntentNullText() {
        api = getValidDialogFlowApi();
        JarvisSession session = api.createSession("sessionID");
        RecognizedIntent intent = api.getIntent(null, session);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getIntentEmptyText() {
        api = getValidDialogFlowApi();
        JarvisSession session = api.createSession("sessionID");
        RecognizedIntent intent = api.getIntent("", session);
    }

    @Test
    public void getIntentUnknownText() {
        api = getValidDialogFlowApi();
        JarvisSession session = api.createSession("sessionID");
        RecognizedIntent intent = api.getIntent("azerty", session);
        assertThat(intent.getDefinition()).as("IntentDefinition is not null").isNotNull();
        assertThat(intent.getDefinition().getName()).as("IntentDefinition is the Default Fallback Intent").isEqualTo
                ("Default Fallback Intent");
    }

}
