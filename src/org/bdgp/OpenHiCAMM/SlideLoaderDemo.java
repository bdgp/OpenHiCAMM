package org.bdgp.OpenHiCAMM;
//import mmcorej.CMMCore;

// TODO: Why does importing SWIG-generated modules not work?
//import org.bdgp.OpenHiCAMM.Modules.PriorSlideLoader.SlideLoaderAPI;
//import org.bdgp.OpenHiCAMM.Modules.PriorSlideLoader.SequenceDoc;
//import static org.bdgp.OpenHiCAMM.Modules.PriorSlideLoader.PriorSlideLoader.getSTATE_STATEMASK;
//import static org.bdgp.OpenHiCAMM.Modules.PriorSlideLoader.PriorSlideLoader.getSTATE_IDLE;
//import static org.bdgp.OpenHiCAMM.Modules.PriorSlideLoader.PriorSlideLoader.getLOADER_ERROR;

public class SlideLoaderDemo {

    public static void main(String[] args) {
        int[] retVal = {0};
        //double[] loadCoords = {-115372.0, -77000.0};
        //double[] initCoords = {0.0, 0.0};
        //String config = "stage_only.cfg";
        String device = "/dev/tty.usbserial-FTEKUITV";

        //CMMCore core = new CMMCore();
        //core.enableDebugLog(false);
        //core.enableStderrLog(false);
        //core.setTimeoutMs(10000);
        //try { core.loadSystemConfiguration(config); }
        //catch (Exception ex) {
        //    System.out.println("Failed to load config file.");
        //    System.exit(-1);
        //}        

        org.bdgp.OpenHiCAMM.Modules.PriorSlideLoader.SlideLoaderAPI sl = 
        		new org.bdgp.OpenHiCAMM.Modules.PriorSlideLoader.SlideLoaderAPI();
        sl.Connect(device, retVal);
        //sl.set_DataLoggingEnabled(true);

        sl.get_Status(retVal);
        reportStatus(retVal[0]);

        // position stage to safe point
        //System.out.println("Positioning stage away from load point.");
        //moveStage(core, initCoords[0], initCoords[1]);
        //System.out.println("Positioned stage at initialization point");

        /* initialize Prior slide loader.....will take about 20 sec.*/
        sl.Initialise(retVal);

        waitForSlideLoader(sl);

        System.out.println("");
        System.out.println("**************************************");
        System.out.println("    Initialize Complete");
        System.out.println("**************************************");

        //System.out.println("Positioning stage at load point.");
        //moveStage(core, loadCoords[0], loadCoords[1]);
        //System.out.println("Positioned stage at load point");

        sl.get_CassettesFitted(retVal);

        sl.ScanCassette(1, retVal);

        waitForSlideLoader(sl);

        System.out.println("");
        System.out.println("**************************************");
        System.out.println("    Cassette Scan Complete");
        System.out.println("**************************************");

        for(int i = 1; i < 32; i++) {
            boolean[] slidePresent = {false};
            sl.get_SlideFitted(1, i, slidePresent);
            if (slidePresent[0]) {
                int slide = i;
                System.out.println("Cassette 1, Slide " + i + " present.");

                sl.MoveToStage(1, slide, retVal);
                waitForSlideLoader(sl);

                System.out.println("**************************************");
                System.out.println("    Load of Slide " + i + " Complete");
                System.out.println("**************************************");
                System.out.println("");

                // This is where the slide surveyor code was
                System.out.println("You could run a slide surveyor here!");

                //System.out.println("Positioning stage at load point.");
                //moveStage(core, loadCoords[0], loadCoords[1]);
                //System.out.println("Positioned stage at load point");

                sl.MoveFromStage(1, slide, retVal);
                waitForSlideLoader(sl);

                System.out.println("**************************************");
                System.out.println("    Unload of Slide " + i + " Complete");
                System.out.println("**************************************");
                System.out.println("");
            }
        }

        sl.DisConnect(retVal);
        System.exit(retVal[0]>0? 1: 0);
    }

    public static void reportStatus(int status) {
        String state = org.bdgp.OpenHiCAMM.Modules.PriorSlideLoader.SequenceDoc.currentState(status);
        String motion = org.bdgp.OpenHiCAMM.Modules.PriorSlideLoader.SequenceDoc.parseMotion(status);
        String errors = "no errors";
        if (org.bdgp.OpenHiCAMM.Modules.PriorSlideLoader.SequenceDoc.errorPresent(status)) {
            errors = org.bdgp.OpenHiCAMM.Modules.PriorSlideLoader.SequenceDoc.parseErrors(status);
        }
        System.out.println("State: "+state);
        System.out.println("State: "+state);
        System.out.println("Motion: "+motion);
        System.out.println("Errors: "+errors);
    }

    //public static void moveStage(CMMCore core, double x, double y) {
    //    core.setTimeoutMs(10000);
    //    //String xyStage = core.getXYStageDevice();
    //    try {
    //          //core.setXYPosition(xyStage, x, y);
    //          // wait for the stage to finish moving
    //          //while (core.deviceBusy(xyStage)) {}
    //    }
    //    catch (Exception e) { 
    //        System.err.println("Failed to move stage to position "+x+","+y);
    //        throw new RuntimeException(e);
    //    }
    //}

    public static int waitForSlideLoader(org.bdgp.OpenHiCAMM.Modules.PriorSlideLoader.SlideLoaderAPI slideLoader) 
    {
        int[] retVal = {0};
        do {
            try { Thread.sleep(1000); } catch (InterruptedException e) { }
            slideLoader.get_Status(retVal);
            //reportStatus(retVal[0]);
        } while (((retVal[0] & org.bdgp.OpenHiCAMM.Modules.PriorSlideLoader.PriorSlideLoader.getSTATE_STATEMASK()) 
              != org.bdgp.OpenHiCAMM.Modules.PriorSlideLoader.PriorSlideLoader.getSTATE_IDLE()) && 
              !((retVal[0] & org.bdgp.OpenHiCAMM.Modules.PriorSlideLoader.PriorSlideLoader.getLOADER_ERROR()) != 0));
        return retVal[0];
    }
}
