#include <map>
#include <iostream>
#include <omero/client.h>
#include <omero/callbacks.h>
#include <omero/cmd/Graphs.h>

using namespace std;

namespace OA = omero::api;
namespace OCB = omero::callbacks;
namespace OCMD = omero::cmd;


/*
 * Subclasses DeleteCallbackI
 */
class Subclass : virtual public OCB::CmdCallbackI {

private:
    Subclass& operator=(const Subclass& rv);
    Subclass(Subclass&);

public:
    Subclass(
        const omero::client_ptr& client,
        const OCMD::HandlePrx& handle) :
        OCB::CmdCallbackI(client, handle, true) {
    };

    void finished(const OCMD::ResponsePtr& _rsp, const OCMD::StatusPtr& status) {
        OCB::CmdCallbackI::finished(_rsp, status);

        OCMD::DeleteRspPtr rsp = OCMD::DeleteRspPtr::dynamicCast(_rsp);

        try {
            if (rsp) {
                // Not an error state then.
                cout << "Report:  deleted=" << rsp->actualDeletes;
                if (!rsp->warning.empty()) {
                    cout << ", warning=" << rsp->warning;
                }
            }
        } catch (const omero::ServerError& se) {
            cout << "Something happened to the handle?!?" << endl;
        }

    };

};

/**
 * Uses the default {@link CmdCallbackI} instance.
 */
int main(int argc, char* argv[]) {

    omero::client_ptr c = new omero::client(); // Close handled by destructor
    OA::ServiceFactoryPrx s = c->createSession();

    {
        OCMD::DeletePtr dc = new OCMD::Delete();
        dc->type = "/Image";
        dc->id = 1;

        OCMD::HandlePrx deleteHandlePrx = s->submit(dc);
        OCB::CmdCallbackIPtr cb = new Subclass(c, deleteHandlePrx); // Closed by destructor

        try {

            cb->loop(10, 500);
            // If we reach here, finished() was called.

        } catch (const omero::LockTimeout& lt) {
            cout << "Not finished in 5 seconds. Cancelling..." << endl;
            if (!deleteHandlePrx->cancel()) {
                cout << "ERROR: Failed to cancel" << endl;
            }
        }
    }
}
