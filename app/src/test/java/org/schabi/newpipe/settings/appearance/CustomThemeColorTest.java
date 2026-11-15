package org.schabi.newpipe.settings.appearance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CustomThemeColorTest {
    @Test
    void normalizesSixDigitHexColors() {
        assertEquals("#C62026", CustomThemeColor.normalize("  #c62026 "));
    }

    @Test
    void rejectsMissingHashAlphaAndIncompleteColors() {
        assertNull(CustomThemeColor.normalize("C62026"));
        assertNull(CustomThemeColor.normalize("#FFC62026"));
        assertNull(CustomThemeColor.normalize("#C62"));
    }
}
