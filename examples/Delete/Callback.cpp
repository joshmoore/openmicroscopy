#include <map>
#include <iostream>
#include <omero/client.h>
#include <omero/callbacks.h>
#include <omero/cmd/Graphs.h>

using namespace std;

namespace OA = omero::api;
namespace OCB = omero::callbacks;
namespace OCMD = omero::cmd;


/**
 * Uses the default {@link DeleteCallbackI} instance.
 */
int main(int argc, char* argv[]) {

    omero::client_ptr c = new omero::client(); // Close handled by destructor
    OA::ServiceFactoryPrx s = c->createSession();

    {
        OCMD::DeletePtr dc = new OCMD::Delete();
        dc->type = "/Image";
        dc->id = 1;

        OCMD::HandlePrx deleteHandlePrx = s->submit(dc);
        OCB::CmdCallbackIPtr cb = new OCB::CmdCallbackI(c, deleteHandlePrx, true);  // Closed by destructor

        try {
            cb->loop(10, 500);

            OCMD::DeleteRspPtr rsp = OCMD::DeleteRspPtr::dynamicCast(
                    cb->getResponse());

            if (rsp) {
                // Not an error state then.
                cout << "Report:  deleted=" << rsp->actualDeletes;
                if (!rsp->warning.empty()) {
                    cout << ", warning=" << rsp->warning;
                }
            }
        } catch (const omero::LockTimeout& lt) {
            cout << "Not finished in 5 seconds. Cancelling..." << endl;
            if (!deleteHandlePrx->cancel()) {
                cout << "ERROR: Failed to cancel" << endl;
            }
        }
    }
}
