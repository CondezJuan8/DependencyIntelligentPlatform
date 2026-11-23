package com.dependencyintelligence.repository.application;

/** Limits shared by import, ZIP storage and the readers of repository sources. */
public final class RepositorySourceLimits {
    public static final int MAX_FILES = 50_000;
    public static final int MAX_ARCHIVE_ENTRIES = 100_000;
    public static final int MAX_PATH_LENGTH = 1_024;
    public static final long MAX_LOCAL_CONTENT_BYTES = 300L * 1024 * 1024;
    public static final long MAX_GITHUB_ZIP_BYTES = 300L * 1024 * 1024;
    public static final long MAX_EXPANDED_BYTES = 512L * 1024 * 1024;
    // A local ZIP includes both local and central headers for every UTF-8 path.
    public static final long MAX_REPLAY_ZIP_BYTES = 640L * 1024 * 1024;

    private RepositorySourceLimits() {}
}
