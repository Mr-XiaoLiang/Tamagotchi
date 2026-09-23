package com.lollipop.grokbot.internal

import java.util.Locale

/**
 * One line of the per-frame curve record, written in the field names of the reference fixture.
 *
 * This is the device half of the fixture comparison. `GrokReferenceFramesTest` replays
 * `reference/frames.txt` in a JVM test; this writes the *same fields* from the running engine to a
 * log sink, so a character that misbehaves on a phone can be read off logcat in the vocabulary of
 * the fixture instead of in ad hoc prints. `GrokBotState.frameTrace` is the sink, and the Demo
 * wires it to `Log.d` from its "curve record" toggle.
 *
 * The record is the scalar *curve* - the springs, the gaze, the blink, the morph - and deliberately
 * not the geometry. `bodyD`, `eye0` and `eye1` are one to four kilobytes each and logcat clips a
 * line at about four, so carrying them would push the numbers that matter out of the record to save
 * the ones that do not: the geometry is covered by the fixture assertions and by the pixel diff of
 * `GrokFrameDumpTest`. `eyeFrom` is left out for the reason the reference test gives - until the
 * sample's first swap it holds page history the port cannot have. `frames`, `reduceMotion`, `rand`,
 * `svgPx` and `error` describe a *sample* rather than a frame.
 *
 * Two fields have no fixture counterpart and mirror the `[mood@pass]` section header instead: `mood`
 * is the mood on screen, and `f` is the frame the host has driven. The header is where the fixture
 * keeps both, and a device has no sample length to report in `frames`.
 *
 * Values are formatted the way the fixture stores them - `%.2f` for the view box and the composed
 * transform, `%.4f` for the springs, `?` for the overlay kind the web build leaves undefined - so a
 * recorded line can be diffed against a `fixed` sample by eye. Fields are separated by `" | "`:
 * `viewBox` and `bodyT` contain spaces of their own, so the separator is part of the format rather
 * than decoration.
 */
internal object GrokFrameTrace {

    /**
     * Appends the record of the frame [engine] currently holds, preceded by the frame index.
     *
     * [frameIndex] is passed in rather than read from the engine so that the counter belongs to the
     * host that drives the frames, which is also the thing that decides when to record at all.
     */
    fun append(out: StringBuilder, frameIndex: Int, engine: GrokEngine) {
        val frame = engine.frame
        val box = frame.viewBox
        out.append("f=").append(frameIndex)
        out.append(" | mood=").append(engine.state.id)
        out.append(" | viewBox=")
            .append(num(box.x, 2)).append(' ')
            .append(num(box.y, 2)).append(' ')
            .append(num(box.size, 2)).append(' ')
            .append(num(box.size, 2))
        // The command list the fixture stores, pivot translate included, rather than the numbers of
        // it: reading the transform back means the port's composition *order* is in the record too.
        out.append(" | bodyT=translate(")
            .append(num(frame.translateX, 2)).append(' ').append(num(frame.translateY, 2))
            .append(") rotate(").append(num(frame.rotation, 2))
            .append(") scale(").append(num(frame.scaleX, 4)).append(' ').append(num(frame.scaleY, 4))
            .append(") translate(").append(num(-frame.pivot, 4)).append(' ')
            .append(num(-frame.pivot, 4)).append(')')
        out.append(" | overlay=").append(num(engine.overlay.x, 4))
        out.append(" | overlayKind=").append(engine.ovKind ?: "?")
        out.append(" | spinTarget=").append(num(engine.spin.t, 4))
        out.append(" | txTarget=").append(num(engine.tx.t, 4))
        out.append(" | tyTarget=").append(num(engine.ty.t, 4))
        out.append(" | squashTarget=").append(num(engine.squash.t, 4))
        out.append(" | eyeCount=").append(frame.eyeFrame.polys.size)
        out.append(" | eyeShow=")
            .append(if (frame.eyeFrame.visible[0]) '1' else '0').append(',')
            .append(if (frame.eyeFrame.visible[1]) '1' else '0')
        out.append(" | gazeX=").append(num(engine.gazeX.t, 4))
        out.append(" | gazeY=").append(num(engine.gazeY.t, 4))
        out.append(" | blinkT=").append(num(engine.blink.t, 4))
        out.append(" | eyeMorphX=").append(num(engine.eyeMorph.x, 4))
        out.append(" | eyeTo=").append(engine.eyeTo)
    }

    /**
     * A fixed point form matching `toFixed` on the page.
     *
     * `Locale.ROOT` is not decoration. The default locale of a device set to a comma decimal
     * separator would otherwise write `gazeX=0,0000`, which is the one value this format exists to be
     * parsed back from.
     */
    private fun num(value: Float, decimals: Int): String =
        String.format(Locale.ROOT, "%.${decimals}f", value)
}
