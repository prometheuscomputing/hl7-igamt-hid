package gov.nist.hit.hl7.igamt.minidump.service;

import java.io.InputStream;

import gov.nist.hit.hl7.igamt.common.base.model.DownloadFile;
import gov.nist.hit.hl7.igamt.minidump.model.MiniDumpValidationResult;

public interface MiniDumpService {
  enum ImportMode {
    OVERRIDE,
    MERGE
  }

  enum ExportFormat {
    JSON,
    BSON,
    BOTH
  }

  DownloadFile exportUserData(String username, ExportFormat format, String archiveName);
  MiniDumpValidationResult validateUserData(String username, InputStream dumpStream);
  void importUserData(String username, InputStream dumpStream, ImportMode mode);
}
