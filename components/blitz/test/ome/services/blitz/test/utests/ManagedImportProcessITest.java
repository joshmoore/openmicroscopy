package ome.services.blitz.test.utests;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import ome.services.blitz.impl.AbstractAmdServant;
import ome.services.blitz.repo.FileMaker;
import ome.services.blitz.repo.ManagedImportProcessI;
import ome.services.blitz.repo.ManagedRepositoryI;
import ome.services.blitz.repo.ProcessCreator;
import ome.services.blitz.repo.RepositoryDao;
import ome.services.blitz.repo.path.FilePathNamingValidator;
import ome.services.blitz.repo.path.FilePathRestrictionInstance;
import ome.services.blitz.repo.path.FsFile;
import omero.ServerError;
import omero.constants.SESSIONUUID;
import omero.model.PermissionsI;
import omero.sys.EventContext;
import omero.util.TempFileManager;

import org.jmock.Mock;
import org.jmock.MockObjectTestCase;
import org.springframework.util.ResourceUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import Ice.Current;
import Ice.Object;
import Ice.ObjectPrx;

@Test(groups = {"fs"})
public class ManagedImportProcessITest extends MockObjectTestCase {

    File tmpdir;

    EventContext ec;

    Mock daoMock;

    RepositoryDao dao;

    FilePathNamingValidator validator;

    ProcessCreator creator;

    ManagedRepositoryI tmri;

    ManagedImportProcessI mipi;

    Ice.Current curr;

    List<FsFile> fsFiles;

    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        String template = lookupTemplate();
        this.tmpdir = TempFileManager.create_path("mip.", ".test", true);
        this.ec = new EventContext();
        this.ec.groupPermissions = new PermissionsI();
        this.ec.userName = "someuser";
        this.daoMock = mock(RepositoryDao.class);
        this.dao = (RepositoryDao) this.daoMock.proxy();
        this.validator = new FilePathNamingValidator(
                FilePathRestrictionInstance.getUnixFilePathRestrictions());
        this.creator = new ProcessCreator(template, dao, validator);
        this.tmri = new ManagedRepositoryI(this.dao, this.creator) {
            @Override
            protected ObjectPrx registerServant(Object tie,
                    AbstractAmdServant servant, Current current)
                    throws ServerError {
                // Doing nothing so that we don't require a proper
                // OmeroContext for the message passing.
                return null;
            }
        };
        this.tmri.initialize(new FileMaker(tmpdir.getAbsolutePath()), -1L, "mockuuid");
        this.curr = new Ice.Current();
        this.curr.ctx = new HashMap<String, String>();
        this.curr.ctx.put(SESSIONUUID.value, "mockuuid");
        this.curr.id = new Ice.Identity("mock-current-name", "mock-current-category");
        this.fsFiles = new ArrayList<FsFile>();
        this.daoMock.expects(atLeastOnce()).method("getEventContext")
            .will(returnValue(ec));
    }

    private String lookupTemplate() throws FileNotFoundException, IOException {
        File properties = ResourceUtils.getFile("classpath:omero.properties");
        Properties p = new Properties();
        p.load(new FileInputStream(properties));
        String template = p.getProperty("omero.fs.repo.path");
        return template;
    }

    private void create() throws ServerError {
        this.mipi = (ManagedImportProcessI)
                this.creator.createProcess(tmri, fsFiles, curr);
    }

    @Test
    public void testTrim() throws Exception {
        fsFiles.add(new FsFile("a.fake"));
        daoMock.expects(atLeastOnce()).method("register");
        daoMock.expects(atLeastOnce()).method("createOrFixUserDir");
        create();
    }
}
