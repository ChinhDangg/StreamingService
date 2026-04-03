package dev.chinh.streamingservice.workers.internal;

import dev.chinh.streamingservice.common.proto.FileRequest;
import dev.chinh.streamingservice.common.proto.FileResponse;
import dev.chinh.streamingservice.common.proto.FileServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
public class FileDiscoveryService {

    @GrpcClient("file-provider")
    private FileServiceGrpc.FileServiceBlockingStub fileStub;

    public FileResponse listFiles(String userId, String id, int page) {
        FileRequest request = FileRequest.newBuilder()
                .setUserId(userId)
                .setId(id)
                .setPage(page)
                .setSortBy("NAME")
                .setSortOrder("ASC")
                .build();

        return fileStub.findFilesInDirectory(request);
    }

    public FileResponse findFileByMId(String userId, long mediaId) {
        FileRequest request = FileRequest.newBuilder()
                .setUserId(userId)
                .setId(String.valueOf(mediaId))
                .build();

        return fileStub.findFileByMId(request);
    }
}
