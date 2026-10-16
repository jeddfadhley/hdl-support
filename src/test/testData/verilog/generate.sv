// Generate blocks, functions and tasks, and a non-ANSI port list.
module adder_tree (a, b, sum);
    parameter int N = 4;
    input  logic [N-1:0][7:0] a, b;
    output logic [N-1:0][8:0] sum;

    function automatic logic [8:0] add(input logic [7:0] x, y);
        return x + y;
    endfunction

    genvar i;
    generate
        for (i = 0; i < N; i++) begin : g_lane
            assign sum[i] = add(a[i], b[i]);
        end
    endgenerate

    if (N > 2) begin : g_wide
        counter #(.WIDTH(4)) u_count (.clk(1'b0), .rst(1'b1), .en(1'b0), .count());
    end else begin : g_narrow
        wire unused;
    end

    case (N)
        1: begin : g_one
            initial $display("one lane");
        end
        default: ;
    endcase

    task automatic report_sum(input int lane);
        $display("lane %0d: %0d", lane, sum[lane]);
    endtask
endmodule
