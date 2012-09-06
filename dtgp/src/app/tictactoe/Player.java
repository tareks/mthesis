
package dtgp.app.tictactoe;

import java.util.Random;
import java.io.Serializable;

public class Player implements Serializable {
    
    public Player() {
	id = new String("");
    }

    public Player(String n) {
	id = n;
    }
      
    public String getId() {
	return id;
    }
    
    public void setId(String s) {
	id = s;
    }
    
    String id;
       
    static final long serialVersionUID = 6625493203230624767L;

}
