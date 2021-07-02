package kz.kcell.wooppay.service.impl;

import kz.kcell.wooppay.entity.PaymentDetails;
import kz.kcell.wooppay.entity.PaymentTransactionDetails;
import kz.kcell.wooppay.entity.Transaction;
import kz.kcell.wooppay.feign.WooppayClient;
import kz.kcell.wooppay.feign.WooppayTestClient;
import kz.kcell.wooppay.mappers.PaymentDetailsMapper;
import kz.kcell.wooppay.mappers.TransactionMapper;
import kz.kcell.wooppay.models.*;
import kz.kcell.wooppay.util.ObjectMapperUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.ibatis.javassist.NotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import team.alabs.kss.mfs.provider.dto.payment.*;

import java.security.SecureRandom;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;



@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTestForPay {

    private static final SecureRandom random = new SecureRandom();

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Mock
    private WooppayClient wooppayClient;
    @Mock
    private WooppayTestClient wooppayTestClient;
    @Mock
    private PaymentDetailsMapper paymentDetailsMapper;
    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private ObjectMapperUtils objectMapperUtils;

    @Captor
    private ArgumentCaptor<Transaction> transactionArgumentCaptor;

    @Captor
    private ArgumentCaptor<PayFromMobileRequest> payFromMobileRequestArgumentCaptor;


    @Test
    void pay_whenTransactionDetailsNotFound_thenCatchException() throws NotFoundException{
        PaymentTransactionRequest underTestPaymentTransactionRequest = new PaymentTransactionRequest();
        underTestPaymentTransactionRequest.setTransactionId(Math.abs(random.nextLong()));

        Mockito.when(paymentDetailsMapper.findTransactionById(underTestPaymentTransactionRequest.getTransactionId())).thenReturn(null);

        Assertions.assertThrows(NotFoundException.class, () -> {
            paymentService.pay(underTestPaymentTransactionRequest);
        },String.format("Transaction with id %d not found", underTestPaymentTransactionRequest.getTransactionId()));
    }

    @Test
    void pay_whenTransactionOperationIdNotNull_thenCatchException() throws Exception{
        PaymentTransactionRequest underTestPaymentTransactionRequest = new PaymentTransactionRequest();
        underTestPaymentTransactionRequest.setTransactionId(Math.abs(random.nextLong()));

        PaymentTransactionDetails paymentTransactionDetails = new PaymentTransactionDetails();
        paymentTransactionDetails.setOperationId(Math.abs(random.nextLong()));


        Mockito.when(paymentDetailsMapper.findTransactionById(underTestPaymentTransactionRequest.getTransactionId())).thenReturn(paymentTransactionDetails);

        Assertions.assertThrows(IllegalStateException.class, () -> {
            paymentService.pay(underTestPaymentTransactionRequest);
                },String.format("Transaction with id = %s is already has operationId", paymentTransactionDetails.getDetailsId())
        );

    }

    @Test
    void pay_whenCannotDeserializeFields_thenCatchException() throws NotFoundException {
        PaymentTransactionRequest underTestTransactionRequest = new PaymentTransactionRequest();
        underTestTransactionRequest.setTransactionId(Math.abs(random.nextLong()));
        PaymentTransactionDetails mockedTransactionDetails = new PaymentTransactionDetails();
        mockedTransactionDetails.setOperationId(null);
        PaymentDetails paymentDetails = new PaymentDetails();
        mockedTransactionDetails.setPaymentDetails(paymentDetails);

        Mockito.when(paymentDetailsMapper.findTransactionById(underTestTransactionRequest.getTransactionId())).thenReturn(mockedTransactionDetails);

        Mockito.when(objectMapperUtils.convert(mockedTransactionDetails.getPaymentDetails().getFields())).thenReturn(null);

        Assertions.assertThrows(RuntimeException.class, () -> {
            paymentService.pay(underTestTransactionRequest);
                }, "Cannot deserialize fields from."
        );
    }

    @Test
    void pay_whenSuccess() throws Exception{
        PaymentTransactionRequest underTestTransactionRequest = new PaymentTransactionRequest();
        underTestTransactionRequest.setTransactionId(Math.abs(random.nextLong()));
        underTestTransactionRequest.setAccount(RandomStringUtils.randomAlphanumeric(10));

        PaymentTransactionDetails transactionDetails = new PaymentTransactionDetails();
        transactionDetails.setOperationId(null);
        PaymentDetails paymentDetails = new PaymentDetails();
        paymentDetails.setServiceName(RandomStringUtils.randomAlphanumeric(5));
        transactionDetails.setPaymentDetails(paymentDetails);

        Mockito.when(paymentDetailsMapper.findTransactionById(underTestTransactionRequest.getTransactionId())).thenReturn(transactionDetails);


        LinkedHashMap fields = new LinkedHashMap();
        Mockito.when(objectMapperUtils.convert(transactionDetails.getPaymentDetails().getFields())).thenReturn(fields);

        Operation operation = new Operation();
        operation.setId(Math.abs(random.nextLong()));
        Operation payment = new Operation();
        payment.setId(Math.abs(random.nextLong()));

        PayFromMobileResponse payFromMobileResponse = PayFromMobileResponse.builder().operation(operation).payment(payment).build();
        Mockito.when(wooppayClient.payFromMobile(any())).thenReturn(payFromMobileResponse);

        paymentService.pay(underTestTransactionRequest);

        Mockito.verify(transactionMapper).updateTransactionsOperationIdAndPaymentOperationId(transactionArgumentCaptor.capture());
        Transaction capturedTransactionValue = transactionArgumentCaptor.getValue();

        assertThat(capturedTransactionValue).isNotNull();
        assertThat(capturedTransactionValue.getOperationId()).isEqualTo(operation.getId());
        assertThat(capturedTransactionValue.getPaymentOperationId()).isEqualTo(payment.getId());


        Mockito.verify(wooppayClient).payFromMobile(payFromMobileRequestArgumentCaptor.capture());
        PayFromMobileRequest captRequestValue = payFromMobileRequestArgumentCaptor.getValue();

        assertThat(captRequestValue).isNotNull();
        assertThat(captRequestValue.getAccountNumber()).isEqualTo(underTestTransactionRequest.getAccount());
        assertThat(captRequestValue.getServiceName()).isEqualTo(transactionDetails.getPaymentDetails().getServiceName());
        assertThat(captRequestValue.getFields()).isEqualTo(fields);
    }

    @Test
    void pay_whenServiceNameIsEmpty() throws NotFoundException {
        PaymentTransactionRequest underTestTransactionRequest = new PaymentTransactionRequest();
        underTestTransactionRequest.setTransactionId(Math.abs(random.nextLong()));
        underTestTransactionRequest.setAccount(RandomStringUtils.randomAlphanumeric(10));

        PaymentTransactionDetails transactionDetails = new PaymentTransactionDetails();
        transactionDetails.setOperationId(null);
        PaymentDetails paymentDetails = new PaymentDetails();
        paymentDetails.setServiceName(null);
        transactionDetails.setPaymentDetails(paymentDetails);

        Mockito.when(paymentDetailsMapper.findTransactionById(underTestTransactionRequest.getTransactionId())).thenReturn(transactionDetails);


        LinkedHashMap fields = new LinkedHashMap();
        Mockito.when(objectMapperUtils.convert(transactionDetails.getPaymentDetails().getFields())).thenReturn(fields);

        Operation operation = new Operation();
        operation.setId(Math.abs(random.nextLong()));
        Operation payment = new Operation();
        payment.setId(Math.abs(random.nextLong()));

        PayFromMobileResponse payFromMobileResponse = PayFromMobileResponse.builder().operation(operation).payment(payment).build();
        Mockito.when(wooppayClient.payFromMobile(any())).thenReturn(payFromMobileResponse);

        paymentService.pay(underTestTransactionRequest);

        Mockito.verify(transactionMapper).updateTransactionsOperationIdAndPaymentOperationId(transactionArgumentCaptor.capture());
        Transaction capturedTransactionValue = transactionArgumentCaptor.getValue();

        assertThat(capturedTransactionValue).isNotNull();
        assertThat(capturedTransactionValue.getOperationId()).isEqualTo(operation.getId());
        assertThat(capturedTransactionValue.getPaymentOperationId()).isEqualTo(payment.getId());


        Mockito.verify(wooppayClient).payFromMobile(payFromMobileRequestArgumentCaptor.capture());
        PayFromMobileRequest captRequestValue = payFromMobileRequestArgumentCaptor.getValue();

        assertThat(captRequestValue).isNotNull();
        assertThat(captRequestValue.getAccountNumber()).isEqualTo(underTestTransactionRequest.getAccount());
        assertThat(captRequestValue.getServiceName()).isNull();
        assertThat(captRequestValue.getFields()).isEqualTo(fields);
    }


    @Test
    void pay_whenSuccess2() throws Exception{
        PaymentTransactionRequest underTestTransactionRequest = new PaymentTransactionRequest();
        underTestTransactionRequest.setTransactionId(Math.abs(random.nextLong()));
        underTestTransactionRequest.setAccount(RandomStringUtils.randomAlphanumeric(10));

        PaymentTransactionDetails transactionDetails = new PaymentTransactionDetails();
        transactionDetails.setOperationId(null);
        PaymentDetails paymentDetails = new PaymentDetails();
        paymentDetails.setServiceName("promo_kcell");
        transactionDetails.setPaymentDetails(paymentDetails);

        Mockito.when(paymentDetailsMapper.findTransactionById(underTestTransactionRequest.getTransactionId())).thenReturn(transactionDetails);


        LinkedHashMap fields = new LinkedHashMap();
        Mockito.when(objectMapperUtils.convert(transactionDetails.getPaymentDetails().getFields())).thenReturn(fields);

        Operation operation = new Operation();
        operation.setId(Math.abs(random.nextLong()));
        Operation payment = new Operation();
        payment.setId(Math.abs(random.nextLong()));

        PayFromMobileResponse payFromMobileResponse = PayFromMobileResponse.builder().operation(operation).payment(payment).build();
        Mockito.when(wooppayTestClient.payFromMobile(any())).thenReturn(payFromMobileResponse);

        paymentService.pay(underTestTransactionRequest);

        Mockito.verify(transactionMapper).updateTransactionsOperationIdAndPaymentOperationId(transactionArgumentCaptor.capture());
        Transaction capturedTransactionValue = transactionArgumentCaptor.getValue();

        assertThat(capturedTransactionValue).isNotNull();
        assertThat(capturedTransactionValue.getOperationId()).isEqualTo(operation.getId());
        assertThat(capturedTransactionValue.getPaymentOperationId()).isEqualTo(payment.getId());


        Mockito.verify(wooppayTestClient).payFromMobile(payFromMobileRequestArgumentCaptor.capture());
        PayFromMobileRequest captRequestValue = payFromMobileRequestArgumentCaptor.getValue();

        assertThat(captRequestValue).isNotNull();
        assertThat(captRequestValue.getAccountNumber()).isEqualTo(underTestTransactionRequest.getAccount());
        assertThat(captRequestValue.getServiceName()).isEqualTo("promo_kcell");
        assertThat(captRequestValue.getFields()).isEqualTo(fields);
    }
}
