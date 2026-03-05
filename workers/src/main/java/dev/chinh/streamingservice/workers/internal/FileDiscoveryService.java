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

    public FileResponse listFiles(String id, int page) {
        FileRequest request = FileRequest.newBuilder()
                .setId(id)
                .setPage(page)
                .setSortBy("NAME")
                .setSortOrder("ASC")
                .build();

        return fileStub.findFilesInDirectory(request);
    }

    public FileResponse findFileByMId(long mediaId) {
        FileRequest request = FileRequest.newBuilder()
                .setId(String.valueOf(mediaId))
                .build();

        return fileStub.findFileByMId(request);
    }
}
