package ome.services.blitz.test.utests;

import java.io.File;
import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import junit.framework.Assert;
import ome.services.blitz.repo.ProcessCreator;
import ome.services.blitz.repo.RepositoryDao;
import ome.services.blitz.repo.path.FilePathNamingValidator;
import ome.services.blitz.repo.path.FilePathRestrictionInstance;
import ome.services.blitz.repo.path.FsFile;
import omero.model.PermissionsI;
import omero.sys.EventContext;
import omero.util.TempFileManager;

import org.jmock.Mock;
import org.jmock.MockObjectTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import Ice.Current;

@Test(groups = {"fs"})
public class ProcessCreatorTest extends MockObjectTestCase {

    Mock daoMock;

    /**
     * The temporary directory which is equivalent to /OMERO/ManagedRepository
     */
    File tmpDir;

    /**
     * The "expanded" template directory which here is mocked to simply
     * "template". This should be used when touch()-ing files under
     * tmpDir.
     */
    File templateDir;

    TestProcessCreator creator;

    Ice.Current curr;

    /**
     * Calendar used to represent "now" in the queries.
     */
    Calendar cal;

    public class TestProcessCreator extends ProcessCreator {

        public TestProcessCreator(String template, RepositoryDao repositoryDao) {
            super(template, repositoryDao, new FilePathNamingValidator(
                    FilePathRestrictionInstance.getUnixFilePathRestrictions()));
        }

        @Override
        public FsFile commonRoot(List<FsFile> paths) {
            return super.commonRoot(paths);
        }

        @Override
        public String expandTemplate(Current curr) {
            return super.expandTemplate(curr);
        }
    }

    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        this.cal = Calendar.getInstance();
        this.tmpDir = TempFileManager.create_path("repo", "test", true);
        this.templateDir = new File(this.tmpDir, "template");
        this.daoMock = mock(RepositoryDao.class);
        this.curr = new Ice.Current();
        this.curr.ctx = new HashMap<String, String>();
        this.curr.ctx.put(omero.constants.SESSIONUUID.value, "TEST");
    }

    private void newCreator(String template) {
        this.creator = new TestProcessCreator(template,
                (RepositoryDao) daoMock.proxy());
    }

    private EventContext newEventContext() {
        EventContext ec = new EventContext();
        ec.userName = "";
        ec.userId = -1L;
        ec.groupName = "";
        ec.groupId = -1L;
        ec.sessionUuid = "";
        ec.sessionId = -1L;
        ec.eventId = -1L;
        ec.groupPermissions = new PermissionsI();
        this.daoMock.expects(atLeastOnce()).method("getEventContext")
            .with(ANYTHING).will(returnValue(ec));
        return ec;
    }

    private static List<FsFile> toFsFileList(String... paths) {
        final List<FsFile> fsFiles = new ArrayList<FsFile>(paths.length);
        for (final String path : paths)
            fsFiles.add(new FsFile(path));
        return fsFiles;
    }

    @Test
    public void testCommonRootReturnsTopLevelWithUncommonPaths() {
        newCreator("ignore");
        FsFile expectedCommonRoot = new FsFile();
        FsFile actualCommonRoot = this.creator.commonRoot(
                toFsFileList("/home/bob/1.jpg", "/data/alice/1.jpg"));
        Assert.assertEquals(expectedCommonRoot, actualCommonRoot);
    }

    @Test
    public void testCommonRootReturnsCommonRootForPathList() {
        newCreator("ignore");
        FsFile expectedCommonRoot = new FsFile("/bob/files/dv");
        FsFile actualCommonRoot = this.creator.commonRoot(toFsFileList(
                expectedCommonRoot + "/file1.dv",
                expectedCommonRoot + "/file2.dv"));
        Assert.assertEquals(expectedCommonRoot, actualCommonRoot);
    }

    @Test
    public void testExpandTemplateEmptyStringOnNullToken() {
        newCreator(null);
        newEventContext();
        String actual = this.creator.expandTemplate(curr);
        Assert.assertEquals(0, actual.length());
    }

    @Test
    public void testExpandTemplateTokenOnMalformedToken() {
        newEventContext();
        String expected = "foo";
        newCreator(expected);
        String actual = this.creator.expandTemplate(curr);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testExpandTemplateYear() {
        newCreator("%year%");
        newEventContext();
        String expected = Integer.toString(cal.get(Calendar.YEAR));
        String actual = this.creator.expandTemplate(curr);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testExpandTemplateMonth() {
        newCreator("%month%");
        newEventContext();
        String expected = Integer.toString(cal.get(Calendar.MONTH)+1);
        if (expected.length() == 1)
            expected = '0' + expected;
        String actual = this.creator.expandTemplate(curr);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testExpandTemplateMonthName() {
        newCreator("%monthname%");
        newEventContext();
        DateFormatSymbols dateFormat = new DateFormatSymbols();
        String expected = dateFormat.getMonths()
                [cal.get(Calendar.MONTH)];
        String actual = this.creator.expandTemplate(curr);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testExpandTemplateDay() {
        newCreator("%day%");
        newEventContext();
        String expected = String.format("%02d", cal.get(Calendar.DAY_OF_MONTH));
        String actual = this.creator.expandTemplate(curr);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testExpandTemplateUserName() {
        newCreator("%user%");
        String expected = "user-1";
        EventContext ecStub = newEventContext();
        ecStub.userName = expected;
        String actual = this.creator.expandTemplate(curr);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testExpandTemplateGroupName() {
        newCreator("%group%");
        String expected = "group-1";
        EventContext ecStub = newEventContext();
        ecStub.groupName = expected;
        String actual = this.creator.expandTemplate(curr);
        Assert.assertEquals(expected, actual);
    }
    @Test
    public void testExpandTemplateGroupNamePerms() {
        newCreator("%group%-%perms%");
        String expected = "group-1-rwrwrw";
        EventContext ecStub = newEventContext();
        ecStub.groupName = "group-1";
        ecStub.groupPermissions = new PermissionsI("rwrwrw");
        String actual = this.creator.expandTemplate(curr);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testExpandTemplateSession() {
        newCreator("%session%");
        String expected = UUID.randomUUID().toString();
        EventContext ecStub = newEventContext();
        ecStub.sessionUuid = expected;
        String actual = this.creator.expandTemplate(curr);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testExpandTemplateEscape() {
        newCreator("%%");
        String expected = "%%";
        newEventContext();
        String actual = this.creator.expandTemplate(curr);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testExpandTemplateEscape2() {
        newCreator("%%-%group%");
        String expected = "%%-grp";
        EventContext ecStub = newEventContext();
        ecStub.groupName = "grp";
        String actual = this.creator.expandTemplate(curr);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testExpandTemplateEscape3() {
        newCreator("%%george");
        String expected = "%%george";
        newEventContext();
        String actual = this.creator.expandTemplate(curr);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testExpandTemplateUnknown() {
        String expected = "%björk%";
        newCreator(expected);
        newEventContext();
        String actual = this.creator.expandTemplate(curr);
        Assert.assertEquals(expected, actual);
    }

}
