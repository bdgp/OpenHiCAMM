package org.bdgp.MMSlide;

public class Main {
    private static boolean commandLineMode = false;
    
    public static void main(String[] args) {
        commandLineMode = true;
        System.exit(0);
    }
    public static boolean isCommandLineMode() {
       return commandLineMode; 
    }
}
