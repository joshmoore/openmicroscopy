package ome.services.blitz.test.utests;

import static omero.rtypes.rstring;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import junit.framework.Assert;
import ome.services.blitz.repo.ManagedRepositoryI;
import ome.services.blitz.repo.ProcessCreator;
import ome.services.blitz.repo.RepositoryDao;
import ome.services.blitz.repo.path.FilePathNamingValidator;
import ome.services.blitz.repo.path.FilePathRestrictionInstance;
import ome.services.blitz.util.ChecksumAlgorithmMapper;
import omero.model.ChecksumAlgorithm;
import omero.model.ChecksumAlgorithmI;

import org.jmock.Mock;
import org.jmock.MockObjectTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.Sets;

@Test(groups = {"fs"})
public class ManagedRepositoryITest extends MockObjectTestCase {

    Mock daoMock;

    RepositoryDao dao;

    ProcessCreator creator;

    ManagedRepositoryI tmri;

    Ice.Current curr;

    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        this.daoMock = mock(RepositoryDao.class);
        this.dao = (RepositoryDao) this.daoMock.proxy();
        this.creator = new ProcessCreator("template", this.dao,
                new FilePathNamingValidator(
                        FilePathRestrictionInstance.getUnixFilePathRestrictions()));
        this.tmri = new ManagedRepositoryI(this.dao, this.creator);
        this.curr = new Ice.Current();

        /*
            File dir = TempFileManager.create_path("mng-repo.", ".test", true);
            initialize(new FileMaker(dir.getAbsolutePath()),
                    -1L /*id, UUID);
        */
    }


    /**
     * Test that the checksum algorithms offered by the managed repository
     * correspond to those listed for enum id
     * <tt>ome.model.enums.ChecksumAlgorithm</tt> in
     * <tt>acquisition.ome.xml</tt>.
     */
    @Test
    public void testListChecksumAlgorithms() {
        final Set<String> expectedAlgorithmNames =
                Sets.newHashSet("Adler-32", "CRC-32", "MD5-128", "Murmur3-32",
                        "Murmur3-128", "SHA1-160");
        for (final ChecksumAlgorithm algorithm :
            this.tmri.listChecksumAlgorithms(curr)) {
            Assert.assertTrue(expectedAlgorithmNames.remove(
                    algorithm.getValue().getValue()));
        }
        Assert.assertTrue(expectedAlgorithmNames.isEmpty());
    }

    /**
     * Test that the server does give checksum algorithm suggestions in accordance with its preferred algorithm.
     */
    @Test
    public void testSuggestFavoredChecksumAlgorithm() {
        final List<ChecksumAlgorithm> configured = this.tmri.listChecksumAlgorithms(curr);
        final ChecksumAlgorithm favored = configured.get(0);
        final String favoredName = ChecksumAlgorithmMapper.CHECKSUM_ALGORITHM_NAMER.apply(favored);

        ChecksumAlgorithm suggestion;
        String suggestionName;

        suggestion = this.tmri.suggestChecksumAlgorithm(Collections.singletonList(favored), curr);
        suggestionName = ChecksumAlgorithmMapper.CHECKSUM_ALGORITHM_NAMER.apply(suggestion);
        Assert.assertEquals(favoredName, suggestionName);

        suggestion = this.tmri.suggestChecksumAlgorithm(configured, curr);
        suggestionName = ChecksumAlgorithmMapper.CHECKSUM_ALGORITHM_NAMER.apply(suggestion);
        Assert.assertEquals(favoredName, suggestionName);
    }

    /**
     * Test that the server does suggest a less-preferred checksum algorithm if the client does not support the preferred.
     */
    @Test
    public void testSuggestUnfavoredChecksumAlgorithm() {
        final List<ChecksumAlgorithm> configured = this.tmri.listChecksumAlgorithms(curr);
        final ChecksumAlgorithm unfavored = configured.get(configured.size() - 1);
        final String unfavoredName = ChecksumAlgorithmMapper.CHECKSUM_ALGORITHM_NAMER.apply(unfavored);

        ChecksumAlgorithm suggestion;
        String suggestionName;

        suggestion = this.tmri.suggestChecksumAlgorithm(Collections.singletonList(unfavored), curr);
        suggestionName = ChecksumAlgorithmMapper.CHECKSUM_ALGORITHM_NAMER.apply(suggestion);
        Assert.assertEquals(unfavoredName, suggestionName);
    }

    /**
     * Test that the server does report when no checksum algorithm is acceptable.
     */
    @Test
    public void testSuggestNoChecksumAlgorithm() {
        final ChecksumAlgorithm badAlgorithm = new ChecksumAlgorithmI();
        badAlgorithm.setValue(rstring(UUID.randomUUID().toString()));

        ChecksumAlgorithm suggestion;

        suggestion = this.tmri.suggestChecksumAlgorithm(Collections.singletonList(badAlgorithm), curr);
        Assert.assertNull(suggestion);
    }
}
