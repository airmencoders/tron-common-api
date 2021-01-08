package mil.tron.commonapi.dto.mapper;

import org.modelmapper.ModelMapper;

public class DtoMapper extends ModelMapper {

    // so that Model Mapper doesn't croak on null properties, notably object Ids...
    @Override
    public <D> D map(Object source, Class<D> destinationType) {
        Object tmpSource = source;
        if(source == null){
            tmpSource = new Object();
        }

        return super.map(tmpSource, destinationType);
    }
}
