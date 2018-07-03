package fr.zelus.jarvis.log.module.action;

import fr.zelus.jarvis.core.session.JarvisContext;

public class LogInfoTest extends LogActionTest {

    private static String INFO_TAG = "[INFO]";

    @Override
    protected LogAction createLogAction(String message) {
        return new LogInfo(logModule, new JarvisContext(), message);
    }

    @Override
    protected String expectedLogTag() {
        return INFO_TAG;
    }
}