-- Counter with a clocked process and attributes.
library IEEE;
use IEEE.STD_LOGIC_1164.ALL;
use ieee.numeric_std.all;

Entity Counter Is
    Generic (WIDTH : Positive := 8);
    Port (
        clk   : in  std_logic;
        rst   : in  std_logic;
        count : out std_logic_vector(WIDTH - 1 downto 0)
    );
End Entity Counter;

architecture rtl of Counter is
    signal value : unsigned(count'range) := (others => '0');
begin
    tick : process (clk) is
    begin
        if clk'event and clk = '1' then
            if rst = '1' then
                value <= (others => '0');
            elsif value = 2**WIDTH - 1 then
                value <= to_unsigned(0, value'length);
            else
                value <= value + 1;
            end if;
        end if;
    end process tick;

    count <= std_logic_vector(value);
end architecture rtl;
