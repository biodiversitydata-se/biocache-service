package au.org.ala.biocache.controller;

import au.org.ala.biocache.dao.JsonPersistentQueueDAOImpl;
import au.org.ala.biocache.dao.PersistentQueueDAO;
import au.org.ala.biocache.service.AuthService;
import au.org.ala.biocache.service.DownloadService;
import au.org.ala.biocache.util.SolrUtils;
import au.org.ala.biocache.web.DownloadController;
import au.org.ala.ws.security.profile.AlaUserProfile;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for occurrence services.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:springTest.xml"})
@WebAppConfiguration
public class DownloadControllerIT extends TestCase {

    static {
        System.setProperty("biocache.config", System.getProperty("user.dir") + "/src/test/resources/biocache-test-config.properties");
    }

    final static AlaUserProfile TEST_USER =
            new AlaUserProfile("Tester","test@test.com", null, null, Collections.EMPTY_SET,Collections.EMPTY_MAP);

    @Autowired
    DownloadController downloadController;

    @Autowired
    DownloadService downloadService;

    PersistentQueueDAO persistentQueueDao;
    AuthService authService;

    @Autowired
    WebApplicationContext wac;

    MockMvc mockMvc;

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @BeforeClass
    public static void setupBeforeClass() throws Exception {
        SolrUtils.setupIndex();
    }

    @Before
    public void setup() throws Exception {

        Path testCacheDir;
        Path testDownloadDir;

        testCacheDir = tempDir.newFolder("downloadcontrolthreadtest-cache").toPath();
        testDownloadDir = tempDir.newFolder("downloadcontrolthreadtest-destination").toPath();
        persistentQueueDao = new JsonPersistentQueueDAOImpl() {
            @Override
            public void init() {
                cacheDirectory = testCacheDir.toAbsolutePath().toString();
                biocacheDownloadDir = testDownloadDir.toAbsolutePath().toString();
                super.init();
            }
        };
        persistentQueueDao.init();

        authService = mock(AuthService.class);

        ReflectionTestUtils.setField(downloadController, "authService", authService);
        ReflectionTestUtils.setField(downloadController, "persistentQueueDAO", persistentQueueDao);

        downloadService.biocacheDownloadDir = testDownloadDir.toAbsolutePath().toString();
        ReflectionTestUtils.setField(downloadService, "persistentQueueDAO", persistentQueueDao);

        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    }

//    @Test
//    public void offlineDownloadValidEmailTestAllAttributes() throws Exception {
//
//        when(authService.getUserDetails("test@test.com"))
//                .thenReturn((Map)ImmutableMap.of(
//                        "activated", true,
//                        "locked", false,
//                        "roles", Arrays.asList("ROLE_USER")
//                ));
//
//        this.mockMvc.perform(get("/occurrences/offline/download")
//                .param("reasonTypeId", "10")
//                .param("email", "test@test.com"))
//                .andExpect(status().isOk())
//                .andExpect(content().contentType("application/json"));
//
//        verify(authService).getUserDetails("test@test.com");
//    }

//    @Test
//    public void offlineDownloadValidEmailTestNoActivated() throws Exception {
//
////        when(authService.getUserDetails("test@test.com"))
////                .thenReturn((Map)ImmutableMap.of(
////                        "locked", false,
////                        "roles", Arrays.asList("ROLE_USER")
////                ));
//
//        this.mockMvc.perform(get("/occurrences/offline/download")
//                .param("reasonTypeId", "10")
//                .param("email", "test@test.com"))
//                .andExpect(status().isOk())
//                .andExpect(content().contentType("application/json"));
//
////        verify(authService).getUserDetails("test@test.com");
//    }

    @Test
    public void downloadTest() throws Exception {

        when(authService.getDownloadUser(any(), any()))
                .thenReturn(Optional.of(new AlaUserProfile("", "test@test.com", null, null, null, null)));

        this.mockMvc.perform(get("/occurrences/offline/download")
                        .param("reasonTypeId", "10")
                        .param("email", "test@test.com"))
                .andExpect(status().isOk());
    }

    @Test
    public void downloadInvalidEmailTest() throws Exception {

        when(authService.getDownloadUser(any(), any()))
                .thenReturn(Optional.empty());

        this.mockMvc.perform(get("/occurrences/offline/download")
                .param("reasonTypeId", "10")
                .param("email", "test@test.com"))
                .andExpect(status().is4xxClientError());

        verify(authService).getDownloadUser(any(), any());
    }



//    @Test
//    public void downloadLockedEmailTest() throws Exception {
//
//        when(authService.getUserDetails("test@test.com"))
//                .thenReturn((Map)ImmutableMap.of(
//                        "activated", true,
//                        "locked", true
//                ));
//
//        this.mockMvc.perform(get("/occurrences/offline/download")
//                .param("reasonTypeId", "10")
//                .param("email", "test@test.com"))
//                .andExpect(status().is4xxClientError());
//
//        verify(authService).getUserDetails("test@test.com");
//    }

//    @Test
//    public void downloadNoRoleEmailTest() throws Exception {
//
//        when(authService.getUserDetails("test@test.com"))
//                .thenReturn((Map)ImmutableMap.of(
//                        "activated", true,
//                        "locked", false,
//                        "roles", Arrays.asList("NO_ROLE")
//                ));
//
//        this.mockMvc.perform(get("/occurrences/offline/download")
//                .param("reasonTypeId", "10")
//                .param("email", "test@test.com"))
//                .andExpect(status().is4xxClientError());
//
//        verify(authService).getUserDetails("test@test.com");
//    }

//    @Test
//    public void downloadActivatedFalseEmailTest() throws Exception {
//
//        when(authService.getUserDetails("test@test.com"))
//                .thenReturn((Map)ImmutableMap.of(
//                        "activated", false,
//                        "locked", false,
//                        "roles", Arrays.asList("ROLE_USER")
//                ));
//
//        this.mockMvc.perform(get("/occurrences/offline/download")
//                .param("reasonTypeId", "10")
//                .param("email", "test@test.com"))
//                .andExpect(status().is4xxClientError());
//
//        verify(authService).getUserDetails("test@test.com");
//    }
}