package com.system_share_documents.DocumentService.controller;

import com.system_share_documents.AppCommonService.dto.request.CreateDocumentKeyRequest;
import com.system_share_documents.DocumentService.dto.ApiResponse;
import com.system_share_documents.DocumentService.dto.request.GetListDocumentVersionRequest;
import com.system_share_documents.DocumentService.dto.response.DocumentVersionResponse;
import com.system_share_documents.DocumentService.service.DocumentVersionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents/version/")
public class DocumentVersionController {

    @Autowired
    private DocumentVersionService documentVersionService;

    @PostMapping("/get/lists")
    public ApiResponse<List<DocumentVersionResponse>> getDocumentVersionController(@RequestBody GetListDocumentVersionRequest request,
                                                                                   @RequestParam(defaultValue = "1") int page,
                                                                                   @RequestParam(defaultValue = "20") int size,
                                                                                   HttpServletRequest httpRequest) throws Exception {
        List<DocumentVersionResponse> response = documentVersionService.getDocumentVersion(request, page, size);
        return ApiResponse.success("OK", "Get list document version of document successfully", response);
    }
}
