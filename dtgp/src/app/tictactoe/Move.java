
package dtgp.app.tictactoe;
import java.io.Serializable;

public class Move implements Serializable {

    public Move() {
	x = y = -1;
    }
    
    public Move(int a, int b) {
	x = a;
	y = b;
    }

    protected int getX() {
	return x;
    }

    protected int getY() {
	return y;
    }

    private int x;
    private int y;

    static final long serialVersionUID = 678111440356455360L;
}
