package fr.zelus.jarvis.core.module.log.action;

import fr.inria.atlanmod.commons.log.Level;

public class LogInfo extends LogAction {

    public LogInfo(String message) {
        super(message, Level.INFO);
    }

}