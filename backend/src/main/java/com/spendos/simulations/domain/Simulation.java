package com.spendos.simulations.domain;

import com.spendos.common.entity.CreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "simulations")
@Getter
@Setter
@NoArgsConstructor
public class Simulation extends CreatedEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "simulation_name", nullable = false, length = 255)
    private String simulationName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scenarios", nullable = false, columnDefinition = "jsonb")
    private String scenarios;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "results", nullable = false, columnDefinition = "jsonb")
    private String results;
}
