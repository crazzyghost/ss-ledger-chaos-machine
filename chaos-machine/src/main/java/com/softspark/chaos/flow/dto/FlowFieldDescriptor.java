package com.softspark.chaos.flow.dto;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

/**
 * Structured metadata for a single flow field, driving how the Single Flow Run form renders and
 * seeds it. The {@code name} is the wire (snake_case) field name the builders read; {@code label}
 * is the human display label (the two differ where the idea's wording diverges from the payload,
 * e.g. {@code narrative} labelled "Source Payment Reference").
 *
 * @param name wire/payload field name (snake_case)
 * @param label human display label
 * @param kind the control/semantic kind
 * @param required shown by default and validated before publish
 * @param advanced non-required and collapsed by default ("needs input but can be inferred")
 * @param defaultValue seed value (e.g. amount {@code 1000.0000}, treasury channel default); nullable
 * @param autogen client auto-generation rule (e.g. UUID v4)
 * @param inference client-side prefill rule from the selected VA
 * @param accountKind for {@link FieldKind#VA_REF}: the VA ownership the picker is scoped to; else null
 * @param slotName for {@link FieldKind#VA_REF}: the flow slot this fills (sent as a slot override); else null
 * @param options for {@link FieldKind#SELECT}: allowed values; else null
 */
@RecordBuilder
public record FlowFieldDescriptor(
    String name,
    String label,
    FieldKind kind,
    boolean required,
    boolean advanced,
    String defaultValue,
    AutogenRule autogen,
    InferenceRule inference,
    AccountKind accountKind,
    String slotName,
    List<String> options) {}
