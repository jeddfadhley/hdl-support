package dev.sal.hdl.verilog

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import dev.sal.hdl.HdlIcons
import javax.swing.Icon
import dev.sal.hdl.verilog.VerilogSyntaxHighlighter as H

/** Settings → Editor → Color Scheme → Verilog/SystemVerilog. */
class VerilogColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName(): String = "Verilog/SystemVerilog"

    override fun getIcon(): Icon = HdlIcons.VERILOG

    override fun getHighlighter(): SyntaxHighlighter = H()

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getDemoText(): String = DEMO_TEXT

    private companion object {
        val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Keyword", H.KEYWORD),
            AttributesDescriptor("Identifier", H.IDENTIFIER),
            AttributesDescriptor("Comments//Line comment", H.LINE_COMMENT),
            AttributesDescriptor("Comments//Block comment", H.BLOCK_COMMENT),
            AttributesDescriptor("String", H.STRING),
            AttributesDescriptor("Number", H.NUMBER),
            AttributesDescriptor("System task or function", H.SYSTEM_TASK),
            AttributesDescriptor("Compiler directive or macro", H.DIRECTIVE),
            AttributesDescriptor("Braces and Operators//Operator", H.OPERATOR),
            AttributesDescriptor("Braces and Operators//Parentheses", H.PARENTHESES),
            AttributesDescriptor("Braces and Operators//Brackets", H.BRACKETS),
            AttributesDescriptor("Braces and Operators//Braces", H.BRACES),
            AttributesDescriptor("Braces and Operators//Semicolon", H.SEMICOLON),
            AttributesDescriptor("Braces and Operators//Comma", H.COMMA),
            AttributesDescriptor("Braces and Operators//Dot", H.DOT),
            AttributesDescriptor("Bad character", H.BAD_CHARACTER),
        )

        val DEMO_TEXT = """
            `timescale 1ns / 1ps
            `define WIDTH 8

            /* A free-running counter with synchronous reset
               and a small state machine. */
            module counter #(
                parameter int W = `WIDTH
            ) (
                input  logic         clk,
                input  logic         rst_n,
                input  logic         start,
                output logic [W-1:0] count,
                output logic         done
            );
                typedef enum logic [1:0] {IDLE, RUN, FINISH} state_t;
                state_t state;

                always_ff @(posedge clk or negedge rst_n) begin
                    if (!rst_n) begin
                        count <= '0;
                        state <= IDLE;
                    end else begin
                        case (state)
                            IDLE:    if (start) state <= RUN;
                            RUN:     begin
                                         count <= count + 1'b1;
                                         if (count == 8'hFE) state <= FINISH;
                                     end
                            FINISH:  state <= IDLE;
                            default: state <= 2'bxx;
                        endcase
                    end
                end

                assign done = (state == FINISH);  // high for one cycle
            endmodule

            module tb;
                logic clk = 0, rst_n, start;
                logic [7:0] count;
                logic done;
                wire \bus+index ;

                counter #(.W(8)) dut (.*);

                always #5ns clk = ~clk;

                initial begin
                    rst_n = 0; start = 0;
                    #20 rst_n = 1;
                    @(posedge clk) start <= 1;
                    wait (done);
                    ${'$'}display("count=%0d width=%0d at %t", count, ${'$'}clog2(256), ${'$'}time);
                    ${'$'}finish;
                end
            endmodule
        """.trimIndent() + "\n"
    }
}
