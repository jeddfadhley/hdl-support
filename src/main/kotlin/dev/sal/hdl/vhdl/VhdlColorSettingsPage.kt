package dev.sal.hdl.vhdl

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import dev.sal.hdl.HdlIcons
import javax.swing.Icon
import dev.sal.hdl.vhdl.VhdlSyntaxHighlighter as H

/** Settings → Editor → Color Scheme → VHDL. */
class VhdlColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName(): String = "VHDL"

    override fun getIcon(): Icon = HdlIcons.VHDL

    override fun getHighlighter(): SyntaxHighlighter = H()

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getDemoText(): String = DEMO_TEXT

    private companion object {
        val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Keyword", H.KEYWORD),
            AttributesDescriptor("Identifier", H.IDENTIFIER),
            AttributesDescriptor("Standard type", H.STD_TYPE),
            AttributesDescriptor("Attribute", H.ATTRIBUTE),
            AttributesDescriptor("Comments//Line comment", H.LINE_COMMENT),
            AttributesDescriptor("Comments//Block comment", H.BLOCK_COMMENT),
            AttributesDescriptor("Literals//String", H.STRING),
            AttributesDescriptor("Literals//Character", H.CHARACTER),
            AttributesDescriptor("Literals//Bit string", H.BIT_STRING),
            AttributesDescriptor("Literals//Number", H.NUMBER),
            AttributesDescriptor("Braces and Operators//Operator", H.OPERATOR),
            AttributesDescriptor("Braces and Operators//Parentheses", H.PARENTHESES),
            AttributesDescriptor("Braces and Operators//Brackets", H.BRACKETS),
            AttributesDescriptor("Braces and Operators//Semicolon", H.SEMICOLON),
            AttributesDescriptor("Braces and Operators//Comma", H.COMMA),
            AttributesDescriptor("Braces and Operators//Dot", H.DOT),
            AttributesDescriptor("Bad character", H.BAD_CHARACTER),
        )

        val DEMO_TEXT = """
            library ieee;
            use ieee.std_logic_1164.all;
            use ieee.numeric_std.all;

            -- An 8-bit counter with synchronous reset.
            entity Counter is
                generic (WIDTH : positive := 8);
                port (
                    clk   : in  std_logic;
                    rst   : in  std_logic;
                    en    : in  std_logic;
                    count : out std_logic_vector(WIDTH - 1 downto 0);
                    wrap  : out std_logic
                );
            end entity Counter;

            ARCHITECTURE rtl OF Counter IS
                signal value : unsigned(WIDTH - 1 downto 0) := (others => '0');
                constant MAX : natural := 2 ** WIDTH - 1;
                constant MASK : std_logic_vector(7 downto 0) := x"FF";
            BEGIN
                /* VHDL-2008 block comment */
                process (clk) is
                begin
                    if clk'event and clk = '1' then
                        if rst = '1' then
                            value <= (others => '0');
                        elsif en = '1' then
                            value <= value + 1;
                        end if;
                    end if;
                end process;

                count <= std_logic_vector(value);
                wrap  <= '1' when value = to_unsigned(MAX, value'length) else '0';

                assert WIDTH <= 32
                    report "WIDTH too large: " & integer'image(WIDTH) & " (limit 16#20#)"
                    severity failure;
            END ARCHITECTURE rtl;
        """.trimIndent() + "\n"
    }
}
