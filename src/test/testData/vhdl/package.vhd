/* VHDL-2008 package with constants and a function. */
library ieee;
use ieee.std_logic_1164.all;

package util_pkg is
    constant MASK  : std_logic_vector(7 downto 0) := x"F0";
    constant BITS  : std_logic_vector := 12UX"A5";
    constant LIMIT : integer := 16#FF#;
    constant DELAY : time := 2.5 ns;
    type state_t is (IDLE, BUSY, DONE);
    type char_set is ('a', 'b', ''');

    function parity(v : std_logic_vector) return std_logic;
end package util_pkg;

package body util_pkg is
    function parity(v : std_logic_vector) return std_logic is
        variable p : std_logic := '0';
    begin
        for i in v'range loop
            p := p xor v(i);
        end loop;
        report "parity of " & integer'image(v'length) & " bits" severity note;
        return p;
    end function;
end package body;
