package org.dataportabilityproject.job;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Map;
import org.dataportabilityproject.cloud.interfaces.PersistentKeyValueStore;
import org.dataportabilityproject.shared.PortableDataType;
import org.dataportabilityproject.shared.auth.AuthData;

/** Provides functionality to manage the lifecycle of a data portability job. */
public class JobManager {
  // Keys for specific values in data store
  private static final String ID_DATA_KEY = "UUID";
  private static final String TOKEN_DATA_KEY = "TOKEN";
  private static final String DATA_TYPE_DATA_KEY = "DATA_TYPE";
  private static final String EXPORT_SERVICE_DATA_KEY = "EXPORT_SERVICE";
  private static final String EXPORT_ACCOUNT_DATA_KEY = "EXPORT_ACCOUNT";
  private static final String EXPORT_INITIAL_AUTH_DATA_KEY = "EXPORT_INITIAL_AUTH_DATA";
  private static final String EXPORT_AUTH_DATA_KEY = "EXPORT_AUTH_DATA";
  private static final String IMPORT_SERVICE_DATA_KEY = "IMPORT_SERVICE";
  private static final String IMPORT_ACCOUNT_DATA_KEY = "IMPORT_ACCOUNT";
  private static final String IMPORT_INITIAL_AUTH_DATA_KEY = "IMPORT_INITIAL_AUTH_DATA";
  private static final String IMPORT_AUTH_DATA_KEY = "IMPORT_AUTH_DATA";


  private final PersistentKeyValueStore storage;

  private final IdProvider idProvider;

  private final TokenManager tokenManager;

  public JobManager(PersistentKeyValueStore storage, IdProvider idProvider,
      TokenManager tokenManager) {
    this.storage = storage;
    this.idProvider = idProvider;
    this.tokenManager = tokenManager;
  }

  /** Creates a new user job and returns a token to identify the job. */
  public String createNewUserjob() throws IOException {
    String newId = idProvider.createId();
    String token = tokenManager.createNewToken(newId);
    storage.put(token, createInitialData(newId, token));
    System.out.println("createNewUserjob, newId: " + newId + " ,token: " + token);
    return token;
  }

  /** Creates a new user job and returns a token to identify the job. */
  public String createNewUserjob(PortableDataType dataType, String exportService,
      String importService) throws IOException {
    String newId = idProvider.createId();
    String token = tokenManager.createNewToken(newId);
    storage.put(token, createInitialData(newId, token, dataType, exportService, importService));
    System.out.println("createNewUserjob, newId: " + newId + " ,token: " + token);
    return token;
  }

  /** Returns the information for a user job or null if not found. */
  public PortabilityJob findExistingJob(String token) {
    Preconditions.checkNotNull(token);
    Map<String, Object> data = storage.get(token);
    System.out.println("\n\ndata: " + data + "\n\n");
    if (data == null || data.isEmpty()) {
      return null;
    }
    return toJob(data);
  }

  /** Replaces the existing entry in storage with the provided {@code job}. */
  public void updateJob(PortabilityJob job) throws IOException {
    Map<String, Object> existing = storage.get(job.token());
    Preconditions.checkArgument(existing != null, "Attempting to updatea  non-exisent job");
    // Store the updated job info
    Map<String, Object> data = fromJob(job);

    System.out.println("updateJob, existing: " + existing);
    System.out.println("updateJob, updated: " + job);

    storage.put(getString(data, TOKEN_DATA_KEY), data);
  }

  /** Converts a {@link PortabilityJob} to key value pairs to persist. */
  private Map<String, Object> fromJob(PortabilityJob job) {
    ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    // Token is the key so it is required
    Preconditions.checkArgument(isPresent(job.token()), "Invalid token");
    builder.put(TOKEN_DATA_KEY, job.token());

    // Token is the key so it is required
    Preconditions.checkArgument(isPresent(job.id()), "Invalid uuid");
    builder.put(ID_DATA_KEY, job.token());

    // Data type may not be set during initial flow
    if(isPresent(job.dataType())) {
      builder.put(DATA_TYPE_DATA_KEY, job.dataType());
    }
    // Validate and add export service information
    if(isPresent(job.exportService())){
      builder.put(EXPORT_SERVICE_DATA_KEY, job.exportService());
    }
    if(isPresent(job.exportAccount())) {
      builder.put(EXPORT_ACCOUNT_DATA_KEY, job.exportAccount());
    }
    if (null != job.exportInitialAuthData()) {
      builder.put(EXPORT_INITIAL_AUTH_DATA_KEY, job.exportInitialAuthData());
    }
    if (null != job.exportAuthData()) {
      builder.put(EXPORT_AUTH_DATA_KEY, job.exportAuthData());
    }
    // Validate and add import service information
    if(isPresent(job.importService())){
      builder.put(IMPORT_SERVICE_DATA_KEY, job.importService());
    }
    if(isPresent(job.importAccount())) {
      builder.put(IMPORT_ACCOUNT_DATA_KEY, job.importAccount());
    }
    if (null != job.importInitialAuthData()) {
      builder.put(IMPORT_INITIAL_AUTH_DATA_KEY, job.importInitialAuthData());
    }
    if (null != job.importAuthData()) {
      builder.put(IMPORT_AUTH_DATA_KEY, job.importAuthData());
    }
    return builder.build();
  }

  /** Converts persisted key value pairs to {@link PortabilityJob}. */
  private PortabilityJob toJob(Map<String, Object> data) {
    Preconditions.checkArgument(isPresent(getString(data, TOKEN_DATA_KEY), "token missing"));
    Preconditions.checkArgument(isPresent(getString(data, ID_DATA_KEY), "uuid missing"));
    // Add required data
    PortabilityJob.Builder builder = PortabilityJob.builder();
    builder.setId(getString(data, ID_DATA_KEY));
    builder.setToken(getString(data, TOKEN_DATA_KEY));

    // newly created sessions will not contain any data type selection
    String dataType = getString(data, DATA_TYPE_DATA_KEY);
    if (dataType == null) {
      return builder.build();
    }
    // Return selected data type
    builder.setDataType(dataType);

    // Return export information if exists
    if (getString(data, EXPORT_SERVICE_DATA_KEY) != null) {
      builder.setExportService(getString(data, EXPORT_SERVICE_DATA_KEY));
    }
    if(getString(data, EXPORT_ACCOUNT_DATA_KEY) != null ) {
      builder.setExportAccount(getString(data, EXPORT_ACCOUNT_DATA_KEY));
    }
    if (data.get(EXPORT_INITIAL_AUTH_DATA_KEY) != null) {
      builder.setExportInitialAuthData((AuthData) data.get(EXPORT_INITIAL_AUTH_DATA_KEY));
    }
    if (data.get(EXPORT_AUTH_DATA_KEY) != null) {
      builder.setExportAuthData((AuthData) data.get(EXPORT_AUTH_DATA_KEY));
    }
    if (getString(data, IMPORT_SERVICE_DATA_KEY) != null) {
      builder.setImportService(getString(data, IMPORT_SERVICE_DATA_KEY));
    }
    if (getString(data, IMPORT_ACCOUNT_DATA_KEY) != null ) {
      builder.setImportAccount(getString(data, IMPORT_ACCOUNT_DATA_KEY));
    }
    if (data.get(IMPORT_INITIAL_AUTH_DATA_KEY) != null) {
      builder.setImportInitialAuthData((AuthData) data.get(IMPORT_INITIAL_AUTH_DATA_KEY));
    }
    if (data.get(IMPORT_AUTH_DATA_KEY) != null) {
      builder.setImportAuthData((AuthData) data.get(IMPORT_AUTH_DATA_KEY));
    }
    return builder.build();
  }

  /** Creates the initial data entry to persist. */
  private static Map<String, Object> createInitialData(String newId, String token) {
    return ImmutableMap.of(ID_DATA_KEY, newId, TOKEN_DATA_KEY, token);
  }

  /** Creates the initial data entry to persist. */
  private static Map<String, Object> createInitialData(String newId, String token,
      PortableDataType dataType, String exportService, String importService) {
    return ImmutableMap.<String, Object>builder()
      .put(ID_DATA_KEY, newId)
      .put(TOKEN_DATA_KEY, token)
      .put(DATA_TYPE_DATA_KEY, dataType.name())
      .put(EXPORT_SERVICE_DATA_KEY, exportService)
      .put(IMPORT_SERVICE_DATA_KEY, importService)
      .build();
  }

  /** Returns true if all values are present and not empty. */
  private static boolean isPresent(String... values) {
    for (String value : values) {
      if (Strings.isNullOrEmpty(value)) {
        return false;
      }
    }
    return true;
  }

  private static String getString(Map<String, Object> map, String key) {
    return (String) map.get(key);
  }
}