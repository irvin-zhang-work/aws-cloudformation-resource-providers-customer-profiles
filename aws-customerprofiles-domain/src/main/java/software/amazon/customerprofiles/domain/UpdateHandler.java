package software.amazon.customerprofiles.domain;

import com.google.common.collect.Lists;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.services.customerprofiles.CustomerProfilesClient;
import software.amazon.awssdk.services.customerprofiles.model.BadRequestException;
import software.amazon.awssdk.services.customerprofiles.model.GetDomainRequest;
import software.amazon.awssdk.services.customerprofiles.model.InternalServerException;
import software.amazon.awssdk.services.customerprofiles.model.ResourceNotFoundException;
import software.amazon.awssdk.services.customerprofiles.model.UntagResourceRequest;
import software.amazon.awssdk.services.customerprofiles.model.UpdateDomainRequest;
import software.amazon.awssdk.services.customerprofiles.model.UpdateDomainResponse;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@NoArgsConstructor
public class UpdateHandler extends BaseHandler<CallbackContext> {

    private CustomerProfilesClient client;

    public UpdateHandler(CustomerProfilesClient client) {
        this.client = client;
    }

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Logger logger) {

        if (this.client == null) {
            this.client = ClientBuilder.getClient();
        }

        final ResourceModel model = request.getDesiredResourceState();

        final GetDomainRequest getDomainRequest = GetDomainRequest.builder()
                .domainName(model.getDomainName())
                .build();

        // If this domain is never created, can not be updated
        try {
            proxy.injectCredentialsAndInvokeV2(getDomainRequest, client::getDomain);
            logger.log(String.format("Get Domain with domainName = %s",
                    model.getDomainName()));
        } catch (BadRequestException e) {
            throw new CfnInvalidRequestException(e);
        } catch (InternalServerException e) {
            throw new CfnServiceInternalErrorException(e);
        } catch (ResourceNotFoundException e) {
            throw new CfnNotFoundException(e);
        } catch (Exception e) {
            throw new CfnGeneralServiceException(e);
        }

        final List<Tag> previousTags = request.getPreviousResourceTags() == null ? Lists.newArrayList() :
                Translator.mapTagsToList(request.getPreviousResourceTags());

        if (previousTags != null) {
            final List<String> tagsToRemove = previousTags.stream()
                    .map(Tag::getKey)
                    .collect(Collectors.toList());

            // Remove previous tags
            if (tagsToRemove.size() > 0) {
                final UntagResourceRequest untagResourceRequest = UntagResourceRequest.builder()
                        .resourceArn(Translator.toDomainARN(request))
                        .tagKeys(tagsToRemove)
                        .build();
                proxy.injectCredentialsAndInvokeV2(untagResourceRequest, client::untagResource);
            }
        }

        final Map<String, String> resourceTag;
        if (request.getDesiredResourceTags() == null) {
            resourceTag = null;
        } else if (request.getDesiredResourceTags().isEmpty()) {
            resourceTag = null;
        } else {
            resourceTag = request.getDesiredResourceTags();
        }
        final UpdateDomainRequest updateDomainRequest = UpdateDomainRequest.builder()
                .domainName(model.getDomainName())
                .deadLetterQueueUrl(model.getDeadLetterQueueUrl())
                .defaultEncryptionKey(model.getDefaultEncryptionKey())
                .defaultExpirationDays(model.getDefaultExpirationDays())
                .tags(resourceTag)
                .build();

        final UpdateDomainResponse updateDomainResponse;
        try {
            updateDomainResponse = proxy.injectCredentialsAndInvokeV2(updateDomainRequest, client::updateDomain);
            logger.log(String.format("Update Domain with domainName = %s",
                    model.getDomainName()));
        } catch (BadRequestException e) {
            throw new CfnInvalidRequestException(e);
        } catch (InternalServerException e) {
            throw new CfnServiceInternalErrorException(e);
        } catch (ResourceNotFoundException e) {
            throw new CfnNotFoundException(e);
        } catch (Exception e) {
            throw new CfnGeneralServiceException(e);
        }

        final ResourceModel responseModel = ResourceModel.builder()
                .createdAt(updateDomainResponse.createdAt().toString())
                .deadLetterQueueUrl(updateDomainResponse.deadLetterQueueUrl())
                .defaultExpirationDays(updateDomainResponse.defaultExpirationDays())
                .defaultEncryptionKey(updateDomainResponse.defaultEncryptionKey())
                .domainName(updateDomainResponse.domainName())
                .lastUpdatedAt(updateDomainResponse.lastUpdatedAt().toString())
                .tags(Translator.mapTagsToList(updateDomainResponse.tags()))
                .build();

        return ProgressEvent.defaultSuccessHandler(responseModel);
    }
}
