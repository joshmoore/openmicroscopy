import java.util.List;

import omero.LockTimeout;
import omero.ServerError;
import omero.api.ServiceFactoryPrx;
import omero.cmd.CmdCallbackI;
import omero.cmd.Delete2;
import omero.cmd.HandlePrx;
import Glacier2.CannotCreateSessionException;
import Glacier2.PermissionDeniedException;

import com.google.common.collect.ImmutableListMultimap;

/**
 * Uses the default {@link DeleteCallbackI} instance.
 */
public class Callback {

    public static void main(String[] args) throws CannotCreateSessionException,
            PermissionDeniedException, ServerError {

        omero.client c = new omero.client();
        ServiceFactoryPrx s = c.createSession();

        try {
            Map<String, List<Long>> 
            Delete2 dc = new Delete2(
                    new ImmutableListMultimap.Builder<String, Long>()
                        .put("Image", 1l).build().asMap(), null, false /* non-dry-run */);
            // TODO: refactor to c.submitAndWait()
            HandlePrx deleteHandlePrx = c.getSession().submit(dc);
            CmdCallbackI cb = new CmdCallbackI(c, deleteHandlePrx);
            try {

                cb.loop(10, 500);

                DeleteReport[] reports = deleteHandlePrx.report();
                DeleteReport r = reports[0]; // We only sent one command
                System.out.println(String.format(
                        "Report:error=%s,warning=%s,deleted=%s", r.error,
                        r.warning, r.actualDeletes));

            } catch (LockTimeout lt) {
                System.out.println("Not finished in 5 seconds. Cancelling...");
                if (!deleteHandlePrx.cancel()) {
                    System.out.println("ERROR: Failed to cancel");
                }
            } finally {
                cb.close(True); // close handle
            }

        } finally {
            c.closeSession();
        }

    }

}
