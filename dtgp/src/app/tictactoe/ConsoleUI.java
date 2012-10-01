
package dtgp.app.tictactoe;

import java.util.Scanner;

public class ConsoleUI {
	public void printBoard(TicTacToe game) {
		System.out.println("Board:");
		for (int i=0;i<3;i++) {
			for (int j=0;j<3;j++) {
				System.out.print(game.getBoard()[j][i]);
			}
			System.out.println();
		}
	}
	
	public void run() {
		Scanner stdin = new Scanner(System.in);
		TicTacToe game = new TicTacToe();
		
		while (!game.isGameOver()) {
		    printBoard(game);
		    System.out.println("Next player: " + game.getNextPlayer());
		    boolean validMove = false;
		    do {
			System.out.println("Enter X and Y coordinates (1-3). Ex: 1 3, or 2 2. You are player: " + game.getNextPlayer());
			int x = stdin.nextInt() - 1;
			int y = stdin.nextInt() - 1;
			try {
			    game.applyMove(game.getNextPlayer(), x, y);
			    validMove = true;
			} catch (Exception e) {
			    System.out.println("Invalid move: " + e.getMessage());
			    System.out.println("Try again.");
			}
		    } while (!validMove);
		}
		printBoard(game);
		System.out.println("Game over. Winner: " + game.getWinner());
	}
	
	public static void main(String[] argv) {
		new ConsoleUI().run();
	}
}

