package com.ankidroid.companion;

public class TemplateOption {
    public long modelId;
    public int ord;
    public String templateName;
    public String modelName;

    public TemplateOption(long modelId, int ord, String templateName, String modelName) {
        this.modelId = modelId;
        this.ord = ord;
        this.templateName = templateName;
        this.modelName = modelName;
    }

    public String displayName() {
        // API does not expose per-template names, so default to an English ordinal.
        String namePart = templateName != null && !templateName.isEmpty() ? templateName : ("Template " + (ord + 1));
        if (modelName != null && !modelName.isEmpty()) {
            return modelName + " • " + namePart;
        }
        return namePart;
    }
}
