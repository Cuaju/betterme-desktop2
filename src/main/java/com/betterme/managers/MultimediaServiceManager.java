package com.betterme.managers;

import MultimediaService.Multimedia;
import MultimediaService.MultimediaServiceGrpc;
import com.betterme.ProgramConfigurations;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MultimediaServiceManager {
    private final String multimediaUri = ProgramConfigurations.getConfiguration()
            .getProperty("multimediaAPI.url");
    private final String multimediaPort = ProgramConfigurations.getConfiguration()
            .getProperty("multimediaAPI.port");

    public byte[] getPostMultimedia(String postId) throws Error {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(multimediaUri, Integer.parseInt(multimediaPort))
                .usePlaintext().build();
        MultimediaServiceGrpc.MultimediaServiceStub stub = MultimediaServiceGrpc.newStub(channel);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        stub.getPostMultimedia(Multimedia.PostInfo.newBuilder().setId(postId).build(),
                new StreamObserver<Multimedia.FileChunk>() {
                    @Override
                    public void onNext(Multimedia.FileChunk fileChunk) {
                        try {
                            buffer.write(fileChunk.getChunk().toByteArray());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        channel.shutdown();
                        throw new Error(throwable.getMessage());
                    }

                    @Override
                    public void onCompleted() {
                        channel.shutdown();
                    }
                });

        return buffer.toByteArray();
    }
}
