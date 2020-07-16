package qslv.transaction.fulfillment;

import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.NonTransientDataAccessResourceException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.ResourceAccessException;
import qslv.common.TimedResponse;
import qslv.common.TraceableRequest;
import qslv.common.kafka.TraceableMessage;
import qslv.transaction.request.CommitReservationRequest;
import qslv.transaction.request.ReservationRequest;
import qslv.transaction.request.TransactionRequest;
import qslv.transaction.request.TransferAndTransactRequest;
import qslv.transaction.response.CommitReservationResponse;
import qslv.transaction.response.ReservationResponse;
import qslv.transaction.response.TransactionResponse;
import qslv.transaction.response.TransferAndTransactReponse;

@Repository
public class TransactionDao {
	private static final Logger log = LoggerFactory.getLogger(TransactionDao.class);
	private static ParameterizedTypeReference<TimedResponse<CommitReservationResponse>> commitResponseType =
			new ParameterizedTypeReference<TimedResponse<CommitReservationResponse>>() {};
	private static ParameterizedTypeReference<TimedResponse<TransactionResponse>> transactionResponseType =
			new ParameterizedTypeReference<TimedResponse<TransactionResponse>>() {};
	private static ParameterizedTypeReference<TimedResponse<ReservationResponse>> reservationResponseType =
			new ParameterizedTypeReference<TimedResponse<ReservationResponse>>() {};
	private static ParameterizedTypeReference<TimedResponse<TransferAndTransactReponse>> transferAndTransactResponseType =
					new ParameterizedTypeReference<TimedResponse<TransferAndTransactReponse>>() {};

	@Autowired
	private ConfigProperties config;
	
	@Autowired
	private RestTemplateProxy restTemplateProxy;
	@Autowired
	private RetryTemplate retryTemplate;

	public void setConfig(ConfigProperties config) {
		this.config = config;
	}
	public void setRestTemplateProxy(RestTemplateProxy restTemplateProxy) {
		this.restTemplateProxy = restTemplateProxy;
	}
	public void setRetryTemplate(RetryTemplate retryTemplate) {
		this.retryTemplate = retryTemplate;
	}
	
	public TransactionResponse recordTransaction(final TraceableMessage<?> message, final TransactionRequest request) {
		log.warn("recordTransaction ENTRY");

		TransactionResponse response = callService(message, config.getPostTransactionUrl(), request, transactionResponseType);

		log.warn("recordTransaction EXIT");
		return response;
	}
	
	public ReservationResponse recordReservation(final TraceableMessage<?> message, final ReservationRequest request) {
		log.warn("recordReservation ENTRY");

		ReservationResponse response = callService(message, config.getPostReservationUrl(), request, reservationResponseType);

		log.warn("recordReservation EXIT");
		return response;
	}
	
	public TransferAndTransactReponse transferAndTransact(final TraceableMessage<?> message, final TransferAndTransactRequest request) {
		log.warn("transferAndTransact ENTRY");

		TransferAndTransactReponse response = callService(message, config.getTransferAndTransactUrl(), request, transferAndTransactResponseType);

		log.warn("transferAndTransact EXIT");
		return response;
	}
	
	public CommitReservationResponse commitReservation(final TraceableMessage<?> message, final CommitReservationRequest request) {
		log.warn("commitReservation ENTRY");

		CommitReservationResponse response = callService(message, config.getCommitReservationUrl(), request, commitResponseType);

		log.warn("commitReservation EXIT");
		return response;
	}
	
	private <M,R> R callService(final TraceableMessage<?> message, String url, M request, ParameterizedTypeReference<TimedResponse<R>> typereference) {
		log.trace("commitReservation ENTRY");

		HttpHeaders headers = buildHeaders(message);
		ResponseEntity<TimedResponse<R>> response = null;
		try {
			response = retryTemplate.execute(new RetryCallback<ResponseEntity<TimedResponse<R>>, ResourceAccessException>() {
				public ResponseEntity<TimedResponse<R>> doWithRetry( RetryContext context) throws ResourceAccessException {
					return restTemplateProxy.exchange(url, HttpMethod.POST,
							new HttpEntity<M>(request, headers), typereference);
			}});
		} catch (ResourceAccessException ex) {
			String msg = String.format("Exhausted %d retries for POST %s.", config.getRestAttempts(), url);
			log.warn(msg);
			throw new TransientDataAccessResourceException(msg, ex);
		} catch (Exception ex) {
			log.error(ex.getLocalizedMessage());
			throw (ex);
		}
		if (!response.hasBody() || !response.getStatusCode().equals(HttpStatus.CREATED) ) {
			String msg = String.format("Unexpected return from %s Service. %s", url, response.toString());
			log.error(msg);
			throw new NonTransientDataAccessResourceException(msg);
		}
		log.trace("commitReservation ENTRY");
		return response.getBody().getPayload();
	}
	
	private HttpHeaders buildHeaders(final TraceableMessage<?> message) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON) );
		headers.add(TraceableRequest.AIT_ID, config.getAitid());
		headers.add(TraceableRequest.BUSINESS_TAXONOMY_ID, message.getBusinessTaxonomyId());
		headers.add(TraceableRequest.CORRELATION_ID, message.getCorrelationId());
		return headers;
	}
}