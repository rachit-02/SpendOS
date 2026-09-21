package com.spendos.imports.domain;

import com.spendos.common.entity.CreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "import_errors")
@Getter
@Setter
@NoArgsConstructor
public class ImportError extends CreatedEntity {

    @Column(name = "import_job_id", nullable = false)
    private UUID importJobId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "raw_data", nullable = false)
    private String rawData;

    @Column(name = "error_message", nullable = false)
    private String errorMessage;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    public ImportError(UUID importJobId, int rowNumber, String rawData, String errorCode, String errorMessage) {
        this.importJobId = importJobId;
        this.rowNumber = rowNumber;
        this.rawData = rawData;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
