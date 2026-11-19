package com.bayflow.auth.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.TenantAsset;
import com.bayflow.auth.domain.TenantAsset.Kind;

@Transactional(readOnly = true)
public interface TenantAssetRepository extends JpaRepository<TenantAsset, UUID> {

    Optional<TenantAsset> findByKind(Kind kind);

    /** Which images the workshop has, and their hashes, without loading the bytes. */
    @Query("select a.kind as kind, a.sha256 as sha256 from TenantAsset a")
    List<AssetVersion> findVersions();

    interface AssetVersion {
        Kind getKind();

        String getSha256();
    }
}
