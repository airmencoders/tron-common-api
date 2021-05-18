package mil.tron.commonapi.controller.advice;

import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import mil.tron.commonapi.annotation.response.WrappedEnvelopeResponse;
import mil.tron.commonapi.dto.response.WrappedResponse;
import mil.tron.commonapi.dto.response.pagination.Pagination;
import mil.tron.commonapi.dto.response.pagination.PaginationLink;
import mil.tron.commonapi.dto.response.pagination.PaginationLinkType;
import mil.tron.commonapi.dto.response.pagination.PaginationWrappedResponse;


/**
 * Controller Advice that wraps a response in an envelope wrapper.
 * Method must be annotated with {@link WrappedEnvelopeResponse} to designate it to be wrapped.
 *
 */
@ControllerAdvice
public class ResponseWrapperAdvice implements ResponseBodyAdvice<Object> {

	@Override
	public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
		return returnType.hasMethodAnnotation(WrappedEnvelopeResponse.class);
	}

	@Override
	public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
			Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
			ServerHttpResponse response) {
		
		// Don't wrap if it's already wrapped
		if (body instanceof PaginationWrappedResponse || body instanceof WrappedResponse) {
			return body;
		}
		
		// Check if response is paginated to set correct headers
		if (body instanceof Page) {
			Page<?> page = (Page<?>)body;
			EnumMap<PaginationLinkType, String> links = createPaginationLinks(page);
			String linkHeader = createPaginationLinkHeader(links);
			
			if (!linkHeader.isBlank()) {
				response.getHeaders().add(HttpHeaders.LINK, linkHeader);
			}
			
			return PaginationWrappedResponse.builder()
				.data(page.getContent())
				.pagination(Pagination.builder()
						.page(page.getPageable().getPageNumber())
						.size(page.getPageable().getPageSize())
						.totalElements(page.getTotalElements())
						.totalPages(page.getTotalPages())
						.links(PaginationLink.builder()
								.first(links.get(PaginationLinkType.FIRST))
								.last(links.get(PaginationLinkType.LAST))
								.next(links.get(PaginationLinkType.NEXT))
								.prev(links.get(PaginationLinkType.PREV))
								.build())
						.build())
				.build();
				
			
		} else if (body instanceof Slice) {
			Slice<?> slice = (Slice<?>)body;
			
			EnumMap<PaginationLinkType, String> links = createPaginationLinks(slice);
			String linkHeader = createPaginationLinkHeader(links);
			
			if (!linkHeader.isBlank()) {
				response.getHeaders().add(HttpHeaders.LINK, linkHeader);
			}
			
			return PaginationWrappedResponse.builder()
					.data(slice.getContent())
					.pagination(Pagination.builder()
							.page(slice.getPageable().getPageNumber())
							.size(slice.getPageable().getPageSize())
							.links(PaginationLink.builder()
									.first(links.get(PaginationLinkType.FIRST))
									.last(links.get(PaginationLinkType.LAST))
									.next(links.get(PaginationLinkType.NEXT))
									.prev(links.get(PaginationLinkType.PREV))
									.build())
							.build())
					.build();
		} 
		
		// Return non-paginated response wrapper
		return WrappedResponse.builder().data(body).build();
	}

	/**
	 * Creates the LINK header string
	 * @param links the links to be converted to header
	 * @return the LINK header string
	 */
	private static String createPaginationLinkHeader(EnumMap<PaginationLinkType, String> links) {
		final StringJoiner header = new StringJoiner(", ");
		for (Map.Entry<PaginationLinkType, String> entry : links.entrySet()) {
			final PaginationLinkType type = entry.getKey();
			final String link = entry.getValue();
			
			header.add(String.format("<%s>; rel=\"%s\"", link, type.toString().toLowerCase()));
		}
		
		return header.toString();
	}
	
	/**
	 * 
	 * Creates map containing possible pagination links for an associated Page or Slice
	 * 
	 * @param <T> Page or Slice
	 * @param pagination the Page or Slice to generate links off of
	 * @return A map containing the applicable pagination links
	 */
	private static <T extends Slice<?>> EnumMap<PaginationLinkType, String> createPaginationLinks(T pagination) {
        final EnumMap<PaginationLinkType, String> links = new EnumMap<>(PaginationLinkType.class);
        
        if (pagination != null) {
        	Pageable pageable = pagination.getPageable();
        	
            if (!pagination.isFirst()) {
                String firstPage = ServletUriComponentsBuilder
                		.fromCurrentRequest()
                		.replaceQueryParam("page", 0)
                		.replaceQueryParam("size", pagination.getSize()).build().encode().toUriString();

                links.put(PaginationLinkType.FIRST, firstPage);
                
            }

            if (pagination.hasPrevious()) {
                final String prevPage = ServletUriComponentsBuilder
                		.fromCurrentRequest()
                		.replaceQueryParam("page", pageable.getPageNumber() - 1)
                		.replaceQueryParam("size", pageable.getPageSize()).build().encode().toUriString();

                links.put(PaginationLinkType.PREV, prevPage);
            }

            if (pagination.hasNext()) {
                final String nextPage = ServletUriComponentsBuilder
                		.fromCurrentRequest()
                		.replaceQueryParam("page", pageable.getPageNumber() + 1)
                		.replaceQueryParam("size", pageable.getPageSize()).build().encode().toUriString();

                links.put(PaginationLinkType.NEXT, nextPage);
            }

            if (!pagination.isLast() && pagination instanceof Page) {
            	final Page<?> page = (Page<?>)pagination;
                final String lastPage = ServletUriComponentsBuilder
                		.fromCurrentRequest()
                		.replaceQueryParam("page", page.getTotalPages() - 1)
                		.replaceQueryParam("size", page.getSize()).build().encode().toUriString();

                links.put(PaginationLinkType.LAST, lastPage);
            }
        }
        
        return links;
	}

}
