-- Top level: instances, generate statements, a record type and a configuration.
library ieee;
use ieee.std_logic_1164.all;
use work.util_pkg.all;

entity top is
    port (
        clk, rst : in  std_logic;
        leds     : out std_logic_vector(7 downto 0)
    );
end entity;

architecture structural of top is
    type bus_t is record
        data  : std_logic_vector(7 downto 0);
        valid : std_logic;
    end record bus_t;

    component Counter is
        generic (WIDTH : positive := 8);
        port (clk, rst : in std_logic; count : out std_logic_vector(WIDTH - 1 downto 0));
    end component;

    signal state  : state_t;
    signal counts : std_logic_vector(15 downto 0);
begin
    u_direct : entity work.counter(rtl)
        generic map (WIDTH => 8)
        port map (clk => clk, rst => rst, count => counts(7 downto 0));

    u_component : Counter port map (clk, rst, counts(15 downto 8));

    g_leds : for i in 0 to 7 generate
        leds(i) <= counts(i) xor counts(i + 8);
    end generate g_leds;

    g_debug : if DELAY > 0 ns generate
        signal seen : boolean;
    begin
        seen <= true when state = DONE else false;
    end generate;

    fsm : process (clk) is
    begin
        if rising_edge(clk) then
            case state is
                when IDLE => state <= BUSY;
                when BUSY =>
                    for i in 0 to 3 loop
                        exit when rst = '1';
                    end loop;
                    state <= DONE;
                when others => state <= IDLE;
            end case;
        end if;
    end process fsm;
end architecture structural;

configuration top_cfg of top is
    for structural
        for u_component : Counter
            use entity work.counter(rtl);
        end for;
    end for;
end configuration top_cfg;
