package com.example.augmentedreality

import com.example.augmentedreality.detection.LabelFilter
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [LabelFilter.normalize].
 *
 * These are pure JVM tests — no Android instrumentation required.
 * Run with: ./gradlew :app:test
 */
class LabelFilterTest {

    // ── Direct allowed labels pass through ──────────────────────────────────

    @Test fun `person is allowed`() =
        assertEquals("person", LabelFilter.normalize("person"))

    @Test fun `laptop is allowed`() =
        assertEquals("laptop", LabelFilter.normalize("laptop"))

    @Test fun `bottle is allowed`() =
        assertEquals("bottle", LabelFilter.normalize("bottle"))

    @Test fun `chair is allowed`() =
        assertEquals("chair", LabelFilter.normalize("chair"))

    @Test fun `cell phone is allowed`() =
        assertEquals("cell phone", LabelFilter.normalize("cell phone"))

    // ── Aliases collapse to canonical labels ─────────────────────────────────

    @Test fun `refrigerator maps to laptop`() =
        assertEquals("laptop", LabelFilter.normalize("refrigerator"))

    @Test fun `tv maps to laptop`() =
        assertEquals("laptop", LabelFilter.normalize("tv"))

    @Test fun `keyboard maps to laptop`() =
        assertEquals("laptop", LabelFilter.normalize("keyboard"))

    @Test fun `monitor maps to laptop`() =
        assertEquals("laptop", LabelFilter.normalize("monitor"))

    @Test fun `book maps to laptop`() =
        assertEquals("laptop", LabelFilter.normalize("book"))

    @Test fun `phone maps to cell phone`() =
        assertEquals("cell phone", LabelFilter.normalize("phone"))

    @Test fun `remote maps to cell phone`() =
        assertEquals("cell phone", LabelFilter.normalize("remote"))

    @Test fun `mobile phone maps to cell phone`() =
        assertEquals("cell phone", LabelFilter.normalize("mobile phone"))

    @Test fun `cup maps to bottle`() =
        assertEquals("bottle", LabelFilter.normalize("cup"))

    @Test fun `couch maps to chair`() =
        assertEquals("chair", LabelFilter.normalize("couch"))

    @Test fun `bench maps to chair`() =
        assertEquals("chair", LabelFilter.normalize("bench"))

    // ── Non-allowed labels are filtered out ──────────────────────────────────

    @Test fun `toothbrush is filtered`() =
        assertNull(LabelFilter.normalize("toothbrush"))

    @Test fun `airplane is filtered`() =
        assertNull(LabelFilter.normalize("airplane"))

    @Test fun `cat is filtered`() =
        assertNull(LabelFilter.normalize("cat"))

    @Test fun `wine glass is filtered`() =
        assertNull(LabelFilter.normalize("wine glass"))

    @Test fun `bicycle is filtered`() =
        assertNull(LabelFilter.normalize("bicycle"))

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test fun `null returns null`() =
        assertNull(LabelFilter.normalize(null))

    @Test fun `blank string returns null`() =
        assertNull(LabelFilter.normalize("   "))

    @Test fun `normalize is case-insensitive for allowed labels`() {
        assertEquals("person", LabelFilter.normalize("PERSON"))
        assertEquals("person", LabelFilter.normalize("Person"))
    }

    @Test fun `normalize is case-insensitive for aliases`() {
        assertEquals("laptop", LabelFilter.normalize("Refrigerator"))
        assertEquals("cell phone", LabelFilter.normalize("REMOTE"))
    }

    @Test fun `normalize trims surrounding whitespace`() {
        assertEquals("person", LabelFilter.normalize("  person  "))
        assertEquals("laptop", LabelFilter.normalize("  tv  "))
    }

    @Test fun `typo alias refridgerator maps to laptop`() =
        assertEquals("laptop", LabelFilter.normalize("refridgerator"))

    // ── COCO80 list sanity checks ─────────────────────────────────────────────

    @Test fun `COCO80 has exactly 80 entries`() =
        assertEquals(80, LabelFilter.COCO80.size)

    @Test fun `all ALLOWED labels are reachable from COCO80 or aliases`() {
        val reachable = LabelFilter.COCO80.toSet() + LabelFilter.ALIASES.keys
        LabelFilter.ALLOWED.forEach { label ->
            assertTrue(
                "'$label' must be reachable from COCO80 or ALIASES",
                label in reachable
            )
        }
    }

    @Test fun `all ALIASES values are in ALLOWED`() {
        LabelFilter.ALIASES.forEach { (alias, canonical) ->
            assertTrue(
                "Alias '$alias' → '$canonical' must resolve to an ALLOWED label",
                canonical in LabelFilter.ALLOWED
            )
        }
    }
}

