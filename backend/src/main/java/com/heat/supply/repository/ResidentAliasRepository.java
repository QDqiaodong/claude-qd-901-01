package com.heat.supply.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.heat.supply.domain.ResidentAlias;

public interface ResidentAliasRepository extends JpaRepository<ResidentAlias, Long> {

    Optional<ResidentAlias> findByAliasTypeAndAliasValue(String aliasType, String aliasValue);

    List<ResidentAlias> findByResidentIdOrderByIdAsc(Long residentId);
}
