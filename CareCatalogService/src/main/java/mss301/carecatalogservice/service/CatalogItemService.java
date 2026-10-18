package mss301.carecatalogservice.service;

import mss301.carecatalogservice.dto.request.CatalogItemRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CatalogItemResponse;

import java.util.List;
import java.util.UUID;

public interface CatalogItemService {
    APIResponse<CatalogItemResponse> create(CatalogItemRequest request);

    APIResponse<CatalogItemResponse> update(UUID id, CatalogItemRequest request);

    APIResponse<List<CatalogItemResponse>> listAll();

    APIResponse<CatalogItemResponse> getById(UUID id);

    APIResponse<Void> delete(UUID id);
}
