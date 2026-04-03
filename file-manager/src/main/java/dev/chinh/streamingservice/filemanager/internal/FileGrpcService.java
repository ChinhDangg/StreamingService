package dev.chinh.streamingservice.filemanager.internal;

import dev.chinh.streamingservice.common.proto.FileItem;
import dev.chinh.streamingservice.common.proto.FileRequest;
import dev.chinh.streamingservice.common.proto.FileResponse;
import dev.chinh.streamingservice.common.proto.FileServiceGrpc;
import dev.chinh.streamingservice.filemanager.constant.SortBy;
import dev.chinh.streamingservice.filemanager.data.FileSystemItem;
import dev.chinh.streamingservice.filemanager.service.FileService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

import java.util.List;

@GrpcService
@RequiredArgsConstructor
public class FileGrpcService extends FileServiceGrpc.FileServiceImplBase {

    private final FileService fileService;

    @Override
    public void findFileByMId(FileRequest request, StreamObserver<FileResponse> responseObserver) {
        FileSystemItem fileItem = fileService.findByMId(request.getUserId(), Long.parseLong(request.getId()));
        if (fileItem == null) {
            responseObserver.onError(Status.NOT_FOUND
                            .withDescription("File was not found with mID: " + request.getId())
                            .asRuntimeException()
            );
            return;
        }

        FileResponse response = FileResponse.newBuilder()
                .addContent(toFileItem(fileItem))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void findFilesInDirectory(FileRequest request, StreamObserver<FileResponse> responseObserver) {

        Slice<FileSystemItem> slice = fileService.findFilesInDirectory(
                request.getUserId(),
                request.getId(),
                request.getPage(),
                SortBy.valueOf(request.getSortBy()),
                Sort.Direction.valueOf(request.getSortOrder())
        );

        List<FileItem> fileItems = slice.getContent()
                .stream()
                .map(this::toFileItem)
                .toList();

        FileResponse response = FileResponse.newBuilder()
                .addAllContent(fileItems)
                .setHasNext(slice.hasNext())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private FileItem toFileItem(FileSystemItem fileItem) {
        return FileItem.newBuilder()
                        .setId(fileItem.getId())
//                        .setParentId(fileItem.getParentId())
//                        .setPath(fileItem.getPath())
                .setName(fileItem.getName())
                .setBucket(fileItem.getBucket() == null ? "" : fileItem.getBucket())
                .setObjectName(fileItem.getObjectName() == null ? "" : fileItem.getObjectName())
                .setSize(fileItem.getSize() == null ? 0 : fileItem.getSize())
                .setLength(fileItem.getLength() == null ? 0 : fileItem.getLength())
                .build();
    }
}
