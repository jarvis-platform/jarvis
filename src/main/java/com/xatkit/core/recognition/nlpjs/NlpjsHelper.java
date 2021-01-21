package com.xatkit.core.recognition.nlpjs;

import com.xatkit.core.recognition.nlpjs.mapper.NlpjsEntityReferenceMapper;
import com.xatkit.intent.BaseEntityDefinition;
import com.xatkit.intent.ContextParameter;
import com.xatkit.intent.CustomEntityDefinition;
import com.xatkit.intent.EntityType;
import com.xatkit.intent.IntentDefinition;

public class NlpjsHelper {

    // This method still needs some improvement in terms of redundant code and performance
    public static ContextParameter getContextParameterFromNlpEntity(String nlpjsEntityType, IntentDefinition intentDefinition,
                                                                    NlpjsEntityReferenceMapper nlpjsEntityReferenceMapper) {
        // The suffixed types do not work with v4 of NLP.js. We need to wait for the update from the guys of NLP.js
        String[] splitEntity = nlpjsEntityType.split("_");
        if (splitEntity.length > 1 && isNumeric(splitEntity[splitEntity.length - 1])) {
            int index = Integer.parseInt(splitEntity[splitEntity.length - 1]);
            String baseEntityType = nlpjsEntityType.substring(0, nlpjsEntityType.lastIndexOf("_"));
            int i = 0;
            for (ContextParameter parameter : intentDefinition.getParameters()) {
                if (parameter.getEntity().getReferredEntity() instanceof BaseEntityDefinition) {
                    if (((BaseEntityDefinition) parameter.getEntity().getReferredEntity()).getEntityType().equals(EntityType.ANY)
                            && nlpjsEntityType.equals(intentDefinition.getName() + parameter.getName() + "Any")) {
                        return parameter;
                    }
                    /*
                     * TODO getReversedEntity should return an empty collection instead of null
                     */
                    if (nlpjsEntityReferenceMapper.getReversedEntity(baseEntityType) != null && nlpjsEntityReferenceMapper.getReversedEntity(baseEntityType).stream().anyMatch(entityType -> entityType.equals(parameter.getEntity().getReferredEntity().getName())) &&
                            (++i == index)) {
                        return parameter;
                    }
                } else if (parameter.getEntity().getReferredEntity() instanceof CustomEntityDefinition) {
                    CustomEntityDefinition customEntityDefinition = (CustomEntityDefinition) parameter.getEntity().getReferredEntity();
                    if (customEntityDefinition.getName().equals(baseEntityType) && (++i == index)) {
                        return parameter;
                    }
                }
            }
        } else {
            for (ContextParameter parameter : intentDefinition.getParameters()) {
                if (parameter.getEntity().getReferredEntity() instanceof BaseEntityDefinition) {
                    if (((BaseEntityDefinition) parameter.getEntity().getReferredEntity()).getEntityType().equals(EntityType.ANY)
                            && nlpjsEntityType.equals(intentDefinition.getName() + parameter.getName() + "Any")) {
                        return parameter;
                    }
                    /*
                     * TODO getReversedEntity should return an empty collection instead of null
                     */
                    if (nlpjsEntityReferenceMapper.getReversedEntity(nlpjsEntityType) != null && nlpjsEntityReferenceMapper.getReversedEntity(nlpjsEntityType).stream().anyMatch(entityType -> entityType.equals(parameter.getEntity().getReferredEntity().getName()))) {
                        return parameter;
                    }
                } else if (parameter.getEntity().getReferredEntity() instanceof CustomEntityDefinition) {
                    CustomEntityDefinition customEntityDefinition = (CustomEntityDefinition) parameter.getEntity().getReferredEntity();
                    if (customEntityDefinition.getName().equals(nlpjsEntityType)) {
                        return parameter;
                    }
                }
            }
        }
        return null;
    }


    private static boolean isNumeric(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

}
