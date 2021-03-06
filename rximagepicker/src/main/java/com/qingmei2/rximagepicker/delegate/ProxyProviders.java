package com.qingmei2.rximagepicker.delegate;

import android.support.v4.app.FragmentActivity;

import com.qingmei2.rximagepicker.core.IImagePickerProcessor;
import com.qingmei2.rximagepicker.core.ImagePickerConfigProvider;
import com.qingmei2.rximagepicker.core.RxImagePicker;
import com.qingmei2.rximagepicker.di.DaggerRxImagePickerComponent;
import com.qingmei2.rximagepicker.di.RxImagePickerComponent;
import com.qingmei2.rximagepicker.di.RxImagePickerModule;
import com.qingmei2.rximagepicker.entity.CustomPickConfigurations;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Single;

public final class ProxyProviders implements InvocationHandler {

    private final IImagePickerProcessor rxImagePickerProcessor;
    private final ProxyTranslator proxyTranslator;
    private final FragmentActivity fragmentActivity;
    private final CustomPickConfigurations customPickConfigurations;

    public ProxyProviders(RxImagePicker.Builder builder,
                          Class<?> providersClass) {
        RxImagePickerComponent component = DaggerRxImagePickerComponent.builder()
                .rxImagePickerModule(new RxImagePickerModule(builder))
                .build();

        rxImagePickerProcessor = component.rxImagePickerProcessor();
        fragmentActivity = component.fragmentActivity();
        proxyTranslator = component.proxyTranslator();
        customPickConfigurations = component.customPickConfigurations();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        return Observable.defer(new Callable<ObservableSource<?>>() {
            @Override
            public ObservableSource<?> call() throws Exception {

                ImagePickerConfigProvider configProvider = proxyTranslator.processMethod(method, args);

                proxyTranslator.instanceProjector(configProvider, fragmentActivity)
                        .display(customPickConfigurations);

                Observable<?> observable = rxImagePickerProcessor.process(configProvider);

                Class<?> methodType = method.getReturnType();

                if (methodType == Observable.class) return Observable.just(observable);

                if (methodType == Single.class)
                    return Observable.just(Single.fromObservable(observable));

                if (methodType == Maybe.class)
                    return Observable.just(Maybe.fromSingle(Single.fromObservable(observable)));

                if (methodType == Flowable.class)
                    return Observable.just(observable.toFlowable(BackpressureStrategy.MISSING));

                throw new RuntimeException(method.getName() + " needs to return one of the next reactive types: observable, single, maybe or flowable");
            }
        }).blockingFirst();
    }
}
