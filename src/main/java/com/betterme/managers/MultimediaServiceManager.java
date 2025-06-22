package com.betterme.managers;

import MultimediaService.Multimedia;
import MultimediaService.MultimediaServiceGrpc;
import com.betterme.ProgramConfigurations;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MultimediaServiceManager {
    private final String multimediaUri = ProgramConfigurations.getConfiguration()
            .getProperty("multimediaAPI.url");
    private final String multimediaPort = ProgramConfigurations.getConfiguration()
            .getProperty("multimediaAPI.port");

    public byte[] getPostMultimedia(String postId) throws Error, IOException, InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(multimediaUri, Integer.parseInt(multimediaPort))
                .usePlaintext().build();
        MultimediaServiceGrpc.MultimediaServiceStub stub = MultimediaServiceGrpc.newStub(channel);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final CountDownLatch finishLatch = new CountDownLatch(1);

        stub.getPostMultimedia(Multimedia.PostInfo.newBuilder().setId(postId).build(),
                new StreamObserver<Multimedia.FileChunk>() {
                    @Override
                    public void onNext(Multimedia.FileChunk fileChunk) {
                        try {
                            buffer.write(fileChunk.getChunk().toByteArray());
                        } catch (IOException e) {
                            finishLatch.countDown();
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        channel.shutdown();
                        finishLatch.countDown();
                        throw new Error(throwable.getMessage());
                    }

                    @Override
                    public void onCompleted() {
                        channel.shutdown();
                        finishLatch.countDown();
                    }
                });

        if (!finishLatch.await(1, TimeUnit.MINUTES)) { // Espera máximo 10 minutos
            System.err.println("La descarga no se completó en el tiempo esperado.");
            throw new RuntimeException("Descarga de multimedia excedió el tiempo límite.");
        }

        try {
            return buffer.toByteArray();
        } finally {
            if (!channel.isShutdown()) {
                channel.shutdown();
            }
        }
    }
}
