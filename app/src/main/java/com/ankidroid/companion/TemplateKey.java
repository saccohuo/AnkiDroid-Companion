package com.ankidroid.companion;

import java.util.Objects;

public class TemplateKey {
    public long modelId;
    public int ord;

    public TemplateKey(long modelId, int ord) {
        this.modelId = modelId;
        this.ord = ord;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemplateKey that = (TemplateKey) o;
        return modelId == that.modelId && ord == that.ord;
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelId, ord);
    }
}
