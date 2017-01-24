package org.fxmisc.richtext;

import static org.fxmisc.richtext.model.TwoDimensional.Bias.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.fxmisc.richtext.model.TwoLevelNavigator;

import javafx.scene.shape.PathElement;
import javafx.scene.text.TextFlow;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;

import com.sun.javafx.geom.RectBounds;
import com.sun.javafx.scene.text.HitInfo;
import com.sun.javafx.scene.text.TextLayout;
import com.sun.javafx.text.PrismTextLayout;
import com.sun.javafx.text.TextLine;

/**
 * Adds additional API to {@link TextFlow}.
 */
class TextFlowExt extends TextFlow {

    private static Method mGetTextLayout;
    private static Method mGetLines;
    private static Method mGetLineIndex;
    private static Method mGetCharCount;
    static {
        try {
            mGetTextLayout = TextFlow.class.getDeclaredMethod("getTextLayout");
            mGetLines = PrismTextLayout.class.getDeclaredMethod("getLines");
            mGetLineIndex = PrismTextLayout.class.getDeclaredMethod("getLineIndex", float.class);
            mGetCharCount = PrismTextLayout.class.getDeclaredMethod("getCharCount");
        } catch (NoSuchMethodException | SecurityException e) {
            throw new RuntimeException(e);
        }
        mGetTextLayout.setAccessible(true);
        mGetLines.setAccessible(true);
        mGetLineIndex.setAccessible(true);
        mGetCharCount.setAccessible(true);
    }

    private static Object invoke(Method m, Object obj, Object... args) {
        try {
            return m.invoke(obj, args);
        } catch (IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    int getLineCount() {
        return getLines().length;
    }

    int getLineStartPosition(int charIdx) {
        TextLine[] lines = getLines();
        TwoLevelNavigator navigator = new TwoLevelNavigator(
                () -> lines.length,
                i -> lines[i].getLength());
        int currentLineIndex = navigator.offsetToPosition(charIdx, Forward).getMajor();
        return navigator.position(currentLineIndex, 0).toOffset();
    }

    int getLineEndPosition(int charIdx) {
        TextLine[] lines = getLines();
        TwoLevelNavigator navigator = new TwoLevelNavigator(
                () -> lines.length,
                i -> lines[i].getLength());
        int currentLineIndex = navigator.offsetToPosition(charIdx, Forward).getMajor();
        int minor = currentLineIndex == lines.length - 1 ? 0 : -1;
        return navigator.position(currentLineIndex + 1, minor).toOffset();
    }

    int getLineOfCharacter(int charIdx) {
        TextLine[] lines = getLines();
        TwoLevelNavigator navigator = new TwoLevelNavigator(
                () -> lines.length,
                i -> lines[i].getLength());
        return navigator.offsetToPosition(charIdx, Forward).getMajor();
    }

    PathElement[] getCaretShape(int charIdx, boolean isLeading) {
        return textLayout().getCaretShape(charIdx, isLeading, 0.0f, 0.0f);
    }

    PathElement[] getRangeShape(int from, int to) {
        return textLayout().getRange(from, to, TextLayout.TYPE_TEXT, 0, 0);
    }

    /**
     * @param from The index of the first character.
     * @param to The index of the last character.
     * @return An array with the PathElement objects which define an
     *         underline from the first to the last character.
     */
    PathElement[] getUnderlineShape(int from, int to) {
        // get a Path for the text underline
        PathElement[] shape = textLayout().getRange(from, to, TextLayout.TYPE_UNDERLINE, 0, 0);

        // The shape is returned as a closed Path (a thin rectangle).
        // If we use the Path as it is, this causes rendering issues.
        // Hence we only use the MoveTo and the succeeding LineTo elements for the result
        // so that simple line segments instead of rectangles are returned.
        List<PathElement> result = new ArrayList<>();

        boolean collect = false;
        for (PathElement elem : shape) {
            if (elem instanceof MoveTo) {   // There seems to be no API to get the type of the PathElement
                result.add(elem);
                collect = true;
            } else if (elem instanceof LineTo) {
                if (collect) {
                    result.add(elem);
                    collect = false;
                }
            }
        }

       return result.toArray(new PathElement[0]);
    }

    CharacterHit hitLine(double x, int lineIndex) {
        return hit(x, getLineCenter(lineIndex));
    }

    CharacterHit hit(double x, double y) {
        HitInfo hit = textLayout().getHitInfo((float) x, (float) y);
        int charIdx = hit.getCharIndex();

        int lineIdx = getLineIndex((float) y);
        if(lineIdx >= getLineCount()) {
            return CharacterHit.insertionAt(getCharCount());
        }

        TextLine line = getLines()[lineIdx];
        RectBounds lineBounds = line.getBounds();

        if(x < lineBounds.getMinX() || x > lineBounds.getMaxX()) {
            if(hit.isLeading()) {
                return CharacterHit.insertionAt(charIdx);
            } else {
                return CharacterHit.insertionAt(charIdx + 1);
            }
        } else {
            if(hit.isLeading()) {
                return CharacterHit.leadingHalfOf(charIdx);
            } else {
                return CharacterHit.trailingHalfOf(charIdx);
            }
        }
    }

    private float getLineY(int index) {
        TextLine[] lines = getLines();
        float spacing = (float) getLineSpacing();
        float lineY = 0;
        for(int i = 0; i < index; ++i) {
            lineY += lines[i].getBounds().getHeight() + spacing;
        }
        return lineY;
    }

    /**
     * @param index The center y position of the line within the text flow.
     *
     * @return The height of the line with the specified index.
     */
    float getLineCenter(int index) {
        return getLineY(index) + getLines()[index].getBounds().getHeight() / 2;
    }

    private TextLine[] getLines() {
        return (TextLine[]) invoke(mGetLines, textLayout());
    }

    private int getLineIndex(float y) {
        return (int) invoke(mGetLineIndex, textLayout(), y);
    }

    private int getCharCount() {
        return (int) invoke(mGetCharCount, textLayout());
    }

    private TextLayout textLayout() {
        return (TextLayout) invoke(mGetTextLayout, this);
    }
}
