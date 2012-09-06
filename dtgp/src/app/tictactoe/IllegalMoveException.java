
package dtgp.app.tictactoe;

public class IllegalMoveException extends Exception {
	public IllegalMoveException(String msg) {
		super(msg);
	}

	private static final long serialVersionUID = 1L; // to shut the compiler up
}
