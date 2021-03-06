// Tools Library
//
// Copyright (C) 2004  Navel Ltd.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//  Email:
//    mhadji@gmail.com

#include <Tools.h>

Tools::Geometry::Point::Point()
	: m_dimension(0), m_pCoords(0)
{
}

Tools::Geometry::Point::Point(const double* pCoords, size_t dimension)
	: m_dimension(dimension)
{
	// no need to initialize m_pCoords to 0 since if a bad_alloc is raised the destructor will not be called.

	m_pCoords = new double[m_dimension];
	memcpy(m_pCoords, pCoords, m_dimension * sizeof(double));
}

Tools::Geometry::Point::Point(const Point& p)
	: m_dimension(p.m_dimension)
{
	// no need to initialize m_pCoords to 0 since if a bad_alloc is raised the destructor will not be called.

	m_pCoords = new double[m_dimension];
	memcpy(m_pCoords, p.m_pCoords, m_dimension * sizeof(double));
}

Tools::Geometry::Point::~Point()
{
	delete[] m_pCoords;
}

Tools::Geometry::Point& Tools::Geometry::Point::operator=(const Point& p)
{
	if (this != &p)
	{
		makeDimension(p.m_dimension);
		memcpy(m_pCoords, p.m_pCoords, m_dimension * sizeof(double));
	}

	return *this;
}

bool Tools::Geometry::Point::operator==(const Point& p) const
{
	if (m_dimension != p.m_dimension)
		throw IllegalArgumentException(
			"Tools::Geometry::Point::operator==: Points have different number of dimensions."
		);

	for (size_t i = 0; i < m_dimension; i++)
	{
		if (
			m_pCoords[i] < p.m_pCoords[i] - std::numeric_limits<double>::epsilon() ||
			m_pCoords[i] > p.m_pCoords[i] + std::numeric_limits<double>::epsilon())  return false;
	}

	return true;
}

//
// IObject interface
//
Tools::Geometry::Point* Tools::Geometry::Point::clone()
{
	return new Point(*this);
}

//
// ISerializable interface
//
size_t Tools::Geometry::Point::getByteArraySize()
{
	return (sizeof(size_t) + m_dimension * sizeof(double));
}

void Tools::Geometry::Point::loadFromByteArray(const byte* ptr)
{
	size_t dimension;
	memcpy(&dimension, ptr, sizeof(size_t));
	ptr += sizeof(size_t);

	makeDimension(dimension);
	memcpy(m_pCoords, ptr, m_dimension * sizeof(double));
	//ptr += m_dimension * sizeof(double);
}

void Tools::Geometry::Point::storeToByteArray(byte** data, size_t& len)
{
	len = getByteArraySize();
	*data = new byte[len];
	byte* ptr = *data;

	memcpy(ptr, &m_dimension, sizeof(size_t));
	ptr += sizeof(size_t);
	memcpy(ptr, m_pCoords, m_dimension * sizeof(double));
	//ptr += m_dimension * sizeof(double);
}

//
// IShape interface
//
bool Tools::Geometry::Point::intersectsShape(const IShape& s) const
{
	const Region* pr = dynamic_cast<const Region*>(&s);
	if (pr != 0)
	{
		return pr->containsPoint(*this);
	}

	throw IllegalStateException(
		"Tools::Geometry::Point::intersectsShape: Not implemented yet!"
	);
}

bool Tools::Geometry::Point::containsShape(const IShape& s) const
{
	return false;
}

bool Tools::Geometry::Point::touchesShape(const IShape& s) const
{
	const Point* ppt = dynamic_cast<const Point*>(&s);
	if (ppt != 0)
	{
		if (*this == *ppt) return true;
		return false;
	}

	const Region* pr = dynamic_cast<const Region*>(&s);
	if (pr != 0)
	{
		return pr->touchesPoint(*this);
	}

	throw IllegalStateException(
		"Tools::Geometry::Point::touchesShape: Not implemented yet!"
	);
}

void Tools::Geometry::Point::getCenter(Point& out) const
{
	out = *this;
}

size_t Tools::Geometry::Point::getDimension() const
{
	return m_dimension;
}

void Tools::Geometry::Point::getMBR(Region& out) const
{
	out = Region(m_pCoords, m_pCoords, m_dimension);
}

double Tools::Geometry::Point::getArea() const
{
	return 0.0;
}

double Tools::Geometry::Point::getMinimumDistance(const IShape& s) const
{
	const Point* ppt = dynamic_cast<const Point*>(&s);
	if (ppt != 0)
	{
		return getMinimumDistance(*ppt);
	}

	const Region* pr = dynamic_cast<const Region*>(&s);
	if (pr != 0)
	{
		return pr->getMinimumDistance(*this);
	}

	throw IllegalStateException(
		"Tools::Geometry::Point::getMinimumDistance: Not implemented yet!"
	);
}

double Tools::Geometry::Point::getMinimumDistance(const Point& p) const
{
	if (m_dimension != p.m_dimension)
		throw IllegalArgumentException(
			"Tools::Geometry::Point::getMinimumDistance: Shapes have different number of dimensions."
		);

	double ret = 0.0;

	for (size_t cDim = 0; cDim < m_dimension; cDim++)
	{
		ret += std::pow(m_pCoords[cDim] - p.m_pCoords[cDim], 2.0);
	}

	return std::sqrt(ret);
}

double Tools::Geometry::Point::getCoordinate(size_t index) const
{
	if (index < 0 || index >= m_dimension)
		throw IndexOutOfBoundsException(index);

	return m_pCoords[index];
}

void Tools::Geometry::Point::makeInfinite(size_t dimension)
{
	makeDimension(dimension);
	for (size_t cIndex = 0; cIndex < m_dimension; cIndex++)
	{
		m_pCoords[cIndex] = std::numeric_limits<double>::max();
	}
}

void Tools::Geometry::Point::makeDimension(size_t dimension)
{
	if (m_dimension != dimension)
	{
		delete[] m_pCoords;

		// remember that this is not a constructor. The object will be destructed normally if
		// something goes wrong (bad_alloc), so we must take care not to leave the object at an intermediate state.
		m_pCoords = 0;

		m_dimension = dimension;
		m_pCoords = new double[m_dimension];
	}
}

std::ostream& Tools::Geometry::operator<<(std::ostream& os, const Point& pt)
{
	for (size_t cDim = 0; cDim < pt.m_dimension; cDim++)
	{
		os << pt.m_pCoords[cDim] << " ";
	}

	return os;
}
