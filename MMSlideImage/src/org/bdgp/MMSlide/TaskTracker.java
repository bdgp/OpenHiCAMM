package org.bdgp.MMSlide;

public class TaskTracker {

	
	
	
	
	protected class TaskStorage implements LineItem {
		public String id = null;
		public int instance = -1;
		public String status = null;
		
		public TaskStorage() {}
		
		public TaskStorage(String id) {
			this.id = id;
		}

		public TaskStorage(String id, String status) {
			this.id = id;
			this.status = status;
		}

		public String [] toTokens() {
			if ( status == null ) {
				String [] t = new String[1];
				t[0] = id;
			}
			String [] t = new String[2];
			t[0] = id;
			t[1] = status;
			return t;
		}
		
		public void fromTokens(String [] line) {
			switch (line.length) {
			case 0:
				return;
			case 2:
				status = line[1];
			case 1:
				id = line[0];
			}
		}
		public String key() {
			return id;
		}
		
	}

}
