package org.schabi.newpipe.learning;

@FunctionalInterface
public interface LearningNoteSaveListener {
    void onSave(long timestampMillis, String noteText);
}
