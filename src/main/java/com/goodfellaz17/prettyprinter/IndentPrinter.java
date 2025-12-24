package com.goodfellaz17.prettyprinter;

/**
 * MontiCore-inspired PrettyPrinter indentation helper.
 * 
 * Manual Ch.8.15: IndentPrinter manages output formatting.
 * Tracks indentation level for hierarchical output.
 */
public class IndentPrinter {
    
    private final StringBuilder buffer;
    private int indentLevel;
    private final String indentString;
    private boolean lineStart;
    
    public IndentPrinter() {
        this("  ");  // Default 2-space indent
    }
    
    public IndentPrinter(String indentString) {
        this.buffer = new StringBuilder();
        this.indentLevel = 0;
        this.indentString = indentString;
        this.lineStart = true;
    }
    
    /**
     * Print text without newline.
     */
    public IndentPrinter print(String text) {
        if (lineStart) {
            buffer.append(indentString.repeat(indentLevel));
            lineStart = false;
        }
        buffer.append(text);
        return this;
    }
    
    /**
     * Print text with newline.
     */
    public IndentPrinter println(String text) {
        print(text);
        buffer.append("\n");
        lineStart = true;
        return this;
    }
    
    /**
     * Print empty newline.
     */
    public IndentPrinter println() {
        buffer.append("\n");
        lineStart = true;
        return this;
    }
    
    /**
     * Print formatted text.
     */
    public IndentPrinter printf(String format, Object... args) {
        print(String.format(format, args));
        return this;
    }
    
    /**
     * Increase indentation level.
     */
    public IndentPrinter indent() {
        indentLevel++;
        return this;
    }
    
    /**
     * Decrease indentation level.
     */
    public IndentPrinter unindent() {
        if (indentLevel > 0) {
            indentLevel--;
        }
        return this;
    }
    
    /**
     * Get current indentation level.
     */
    public int getIndentLevel() {
        return indentLevel;
    }
    
    /**
     * Clear the buffer.
     */
    public void clear() {
        buffer.setLength(0);
        indentLevel = 0;
        lineStart = true;
    }
    
    /**
     * Get the formatted output.
     */
    public String getContent() {
        return buffer.toString();
    }
    
    @Override
    public String toString() {
        return getContent();
    }
}
