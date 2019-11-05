package org.opentripplanner.netex.loader.mapping;

import org.opentripplanner.model.ServiceCalendarDate;
import org.opentripplanner.model.calendar.ServiceDate;
import org.opentripplanner.netex.loader.util.ReadOnlyHierarchicalMap;
import org.opentripplanner.netex.loader.util.ReadOnlyHierarchicalMapById;
import org.opentripplanner.netex.support.DayTypeRefsToServiceIdAdapter;
import org.rutebanken.netex.model.DayType;
import org.rutebanken.netex.model.DayTypeAssignment;
import org.rutebanken.netex.model.OperatingPeriod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static org.opentripplanner.model.ServiceCalendarDate.EXCEPTION_TYPE_ADD;
import static org.opentripplanner.model.ServiceCalendarDate.EXCEPTION_TYPE_REMOVE;

// TODO OTP2 - Add Unit tests
//           - JavaDoc needed
class CalendarMapper {
    private static final Logger LOG = LoggerFactory.getLogger(CalendarMapper.class);

    private final FeedScopedIdFactory idFactory;
    private final ReadOnlyHierarchicalMap<String, Collection<DayTypeAssignment>> dayTypeAssignmentByDayTypeId;
    private final ReadOnlyHierarchicalMapById<OperatingPeriod> operatingPeriodById;
    private final ReadOnlyHierarchicalMapById<DayType> dayTypeById;


    CalendarMapper(
            FeedScopedIdFactory idFactory,
            ReadOnlyHierarchicalMap<String, Collection<DayTypeAssignment>> dayTypeAssignmentByDayTypeId,
            ReadOnlyHierarchicalMapById<OperatingPeriod> operatingPeriodById,
            ReadOnlyHierarchicalMapById<DayType> dayTypeById
    ) {
        this.idFactory = idFactory;
        this.dayTypeAssignmentByDayTypeId = dayTypeAssignmentByDayTypeId;
        this.operatingPeriodById = operatingPeriodById;
        this.dayTypeById = dayTypeById;
    }

    Collection<ServiceCalendarDate> mapToCalendarDates(DayTypeRefsToServiceIdAdapter dayTypeRefs) {
        String serviceId = dayTypeRefs.getServiceId();

        // The mapper store intermediate results and need to be initialized every time
        DayTypeAssignmentMapper dayTypeAssignmentMapper = new DayTypeAssignmentMapper(dayTypeById, operatingPeriodById);

        for (String dayTypeId : dayTypeRefs.getDayTypeRefs()) {
            dayTypeAssignmentMapper.mapAll(dayTypeId, dayTypeAssignments(dayTypeId));
        }

        Set<LocalDateTime> dates = dayTypeAssignmentMapper.mergeDates();

        if (dates.isEmpty()) {
            LOG.warn("ServiceCode " + serviceId + " does not contain any serviceDates");
            // Add one date exception when list is empty to ensure serviceId is not lost
            LocalDateTime today = LocalDate.now().atStartOfDay();
            return Collections.singleton(
                    newServiceCalendarDate(today, serviceId, EXCEPTION_TYPE_REMOVE)
            );
        }
        return dates.stream()
                .map(it -> newServiceCalendarDate(it, serviceId, EXCEPTION_TYPE_ADD))
                .collect(Collectors.toList());
    }

    private Collection<DayTypeAssignment> dayTypeAssignments(String dayTypeId) {
        return dayTypeAssignmentByDayTypeId.lookup(dayTypeId);
    }

    private ServiceCalendarDate newServiceCalendarDate(
            LocalDateTime date, String serviceId, Integer exceptionType
    ) {
        ServiceCalendarDate serviceCalendarDate = new ServiceCalendarDate();
        serviceCalendarDate.setServiceId(idFactory.createId(serviceId));
        serviceCalendarDate.setDate(
                new ServiceDate(date.getYear(), date.getMonthValue(), date.getDayOfMonth()));
        serviceCalendarDate.setExceptionType(exceptionType);
        return serviceCalendarDate;
    }
}