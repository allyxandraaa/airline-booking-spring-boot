package com.example.payment_service.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

final class StreamCollector<T> implements StreamObserver<T> {

	private final List<T> values = new CopyOnWriteArrayList<>();
	private final CountDownLatch done = new CountDownLatch(1);
	private volatile Throwable error;

	@Override
	public void onNext(T value) {
		values.add(value);
	}

	@Override
	public void onError(Throwable t) {
		error = t;
		done.countDown();
	}

	@Override
	public void onCompleted() {
		done.countDown();
	}

	StreamCollector<T> await() throws InterruptedException {
		assertThat(done.await(10, TimeUnit.SECONDS)).as("stream finished in time").isTrue();
		return this;
	}

	List<T> values() {
		return values;
	}

	Status.Code errorCode() {
		return error == null ? null : Status.fromThrowable(error).getCode();
	}
}
